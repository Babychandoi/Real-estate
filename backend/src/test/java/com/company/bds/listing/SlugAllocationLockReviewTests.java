package com.company.bds.listing;

import com.company.bds.listing.application.command.CreateListingDraftCommand;
import com.company.bds.listing.application.service.ListingApplicationService;
import com.company.bds.listing.domain.model.ListingAttributes;
import com.company.bds.listing.domain.model.ListingPurpose;
import com.company.bds.listing.domain.model.PropertyType;
import com.company.bds.testsupport.BdsIntegrationTest;
import com.company.bds.testsupport.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Adversarial review of the W6 slug advisory lock (PR #24). {@code pg_advisory_xact_lock} is held until the enclosing
 * transaction ends, and a CSV import ({@code ListingImportService.importCsv}) creates every draft in ONE transaction. Two
 * imports whose rows share titles in opposite order take the per-title locks in opposite order: a deadlock, and one
 * whole import is rolled back. Expected to fail on the reviewed commit.
 */
@BdsIntegrationTest
class SlugAllocationLockReviewTests {
    @Autowired ListingApplicationService listings;
    @Autowired PlatformTransactionManager transactions;
    @Autowired TestData data;

    @Test
    void twoImportsWithTheSameTitlesInOppositeOrderBothCommit() throws Exception {
        String token = UUID.randomUUID().toString().substring(0, 8);
        String x = "Nhà phố mặt tiền " + token + " alpha";
        String y = "Nhà phố mặt tiền " + token + " beta";
        UUID first = data.user().role("BROKER").verifiedKyc().create().id();
        UUID second = data.user().role("BROKER").verifiedKyc().create().id();
        CyclicBarrier bothHoldTheirFirstTitle = new CyclicBarrier(2);
        TransactionTemplate tx = new TransactionTemplate(transactions);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<?>> imports = new ArrayList<>();
            for (Object[] plan : List.of(new Object[]{first, x, y}, new Object[]{second, y, x})) {
                imports.add(pool.submit(() -> tx.executeWithoutResult(status -> {
                    listings.createImportedDraft(command((UUID) plan[0], (String) plan[1]), null);
                    try {
                        bothHoldTheirFirstTitle.await(30, TimeUnit.SECONDS);
                    } catch (Exception ex) {
                        throw new IllegalStateException(ex);
                    }
                    listings.createImportedDraft(command((UUID) plan[0], (String) plan[2]), null);
                })));
            }
            List<Throwable> failures = new ArrayList<>();
            for (Future<?> f : imports) {
                try {
                    f.get(60, TimeUnit.SECONDS);
                } catch (java.util.concurrent.ExecutionException ex) {
                    failures.add(ex.getCause());
                }
            }
            assertThat(failures).as("concurrent imports sharing titles must both commit (no advisory-lock deadlock)").isEmpty();
        } finally {
            pool.shutdownNow();
        }
    }

    private static CreateListingDraftCommand command(UUID owner, String title) {
        return new CreateListingDraftCommand(owner, title, ListingPurpose.SALE, PropertyType.HOUSE, 5_000_000_000L, new BigDecimal("80"),
                3, 2, 3, null, null, null, null, "Nhà phố mặt tiền, kiểm thử khóa cấp phát slug đồng thời.", "01", "005", null,
                "Cầu Giấy, Hà Nội", null, null, null, ListingAttributes.EMPTY);
    }
}
