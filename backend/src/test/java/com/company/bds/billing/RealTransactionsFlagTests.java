package com.company.bds.billing;

import com.company.bds.testsupport.BdsIntegrationTest;
import com.company.bds.testsupport.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P-11: the platform sells posting packages only. Property deposit/escrow endpoints stay disabled unless
 * {@code FEATURE_REAL_TRANSACTIONS=true} (the application default is false; the shared test profile turns it on).
 */
@BdsIntegrationTest(properties = "app.features.real-transactions=false")
class RealTransactionsFlagTests {
    @Autowired MockMvc mvc;
    @Autowired TestData data;

    @Test
    void depositAndEscrowEndpointsAreUnavailableWithoutTheFeatureFlag() throws Exception {
        TestData.TestUser buyer = data.user().verifiedKyc().create();
        TestData.TestListing listing = data.listing(data.user().role("OWNER").create().id()).create();
        String bearer = "Bearer " + data.sessionFor(buyer.id());
        mvc.perform(post("/api/v1/transactions/deposits").header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"listingId\":\"" + listing.id() + "\",\"depositAmountVnd\":50000000,\"totalPropertyPriceVnd\":3000000000,"
                                + "\"buyerFullName\":\"Người mua\",\"buyerIdNumber\":\"001201014567\",\"sellerFullName\":\"Người bán\"}"))
                .andExpect(status().is(org.hamcrest.Matchers.oneOf(400, 503)));
        String admin = "Bearer " + data.sessionFor(data.user().role("ADMIN").create().id());
        mvc.perform(post("/api/v1/transactions/deposits/" + UUID.randomUUID() + "/release").header("Authorization", admin))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("FEATURE_UNAVAILABLE"));
    }
}
