package com.company.bds.billing;

import com.company.bds.shared.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Service packages (posting quota). Admin paths are ADMIN only (SecurityConfig). */
@RestController
@RequestMapping("/api/v1/billing")
public class BillingController {
    private final BillingService service;

    public BillingController(BillingService service) { this.service = service; }

    @GetMapping("/plans")
    public List<BillingService.Plan> plans() { return service.plans(); }

    /** The user's own orders, newest first, paged. */
    @GetMapping("/orders")
    public BillingService.OrderPage mine(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size,
                                         Authentication a) {
        return service.mine(CurrentUser.id(a), page, size);
    }

    @GetMapping("/orders/{id}")
    public BillingService.OrderDetail mineDetail(@PathVariable UUID id, Authentication a) {
        return service.detail(id, CurrentUser.id(a), false);
    }

    /**
     * 200 with the new order, or with the existing open order of the same plan / the order of a replayed Idempotency-Key
     * ({@code X-Order-Reused: true}); status kept 200 in both cases for existing clients.
     */
    @PostMapping("/orders")
    public ResponseEntity<BillingService.Order> create(@Valid @RequestBody PlanRequest r,
                                                       @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                                       Authentication a) {
        BillingService.CreateResult result = service.create(CurrentUser.id(a), r.planCode(), idempotencyKey);
        return ResponseEntity.ok().header("X-Order-Reused", String.valueOf(!result.created())).body(result.order());
    }

    @PostMapping("/orders/{id}/reported")
    public BillingService.Order report(@PathVariable UUID id, Authentication a) { return service.report(id, CurrentUser.id(a)); }

    @PostMapping("/orders/{id}/cancel")
    public BillingService.Order cancel(@PathVariable UUID id, Authentication a) { return service.cancel(id, CurrentUser.id(a)); }

    @GetMapping("/admin/bank")
    public BillingService.BankSettings bank() { return service.bank(); }

    /** Compare-and-set with {@code expectedVersion} (the version loaded by the admin); 409 BANK_SETTINGS_CONFLICT when stale. */
    @PutMapping("/admin/bank")
    public BillingService.BankSettings bank(@RequestBody BankRequest b) {
        return service.saveBank(new BillingService.BankSettings(b.bankBin(), b.bankName(), b.accountNumber(), b.accountName(), b.adminEmail(), 0),
                b.expectedVersion());
    }

    @GetMapping("/admin/reconciliation")
    public BillingService.AdminOrderPage queue(@RequestParam(required = false) String status,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return service.reconciliation(status, page, size);
    }

    @GetMapping("/admin/orders")
    public BillingService.AdminOrderPage orders(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
                                                @RequestParam(required = false) String status, @RequestParam(required = false) String q) {
        return service.adminOrders(page, size, status, q);
    }

    @GetMapping("/admin/orders/{id}")
    public BillingService.OrderDetail orderDetail(@PathVariable UUID id) { return service.detail(id, null, true); }

    /*
     * Admin review actions. Each runs under the order's row lock and changes it only from the states it accepts, so
     * concurrent or repeated actions have one effect and the others get 409 ORDER_STATE_CHANGED. With an optional
     * Idempotency-Key (scoped to the admin, bound to the action, order and payload, kept 24 h) a retry after a lost
     * response gets the committed result back with "Idempotent-Replayed: true" instead of a 409.
     */

    @PostMapping("/admin/reconciliation/{id}/receipt")
    public ResponseEntity<BillingService.Order> receipt(@PathVariable UUID id, @Valid @RequestBody ReceiptRequest r,
                                                        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                                        Authentication a) {
        return reviewed(service.recordReceipt(id, CurrentUser.id(a), r.receivedAmountVnd(), r.receivedReference(), r.note(), idempotencyKey));
    }

    @PostMapping("/admin/reconciliation/{id}/resolve")
    public ResponseEntity<BillingService.Order> resolve(@PathVariable UUID id, @Valid @RequestBody ResolveRequest r,
                                                        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                                        Authentication a) {
        return reviewed(service.resolveException(id, CurrentUser.id(a), r.resolution(), r.note(), idempotencyKey));
    }

    @PostMapping("/admin/reconciliation/{id}/approve")
    public ResponseEntity<BillingService.Order> approve(@PathVariable UUID id, @RequestBody(required = false) ReviewRequest r,
                                                        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                                        Authentication a) {
        return reviewed(service.approve(id, CurrentUser.id(a), r == null ? null : r.note(), idempotencyKey));
    }

    @PostMapping("/admin/reconciliation/{id}/reject")
    public ResponseEntity<BillingService.Order> reject(@PathVariable UUID id, @Valid @RequestBody RejectRequest r,
                                                       @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                                       Authentication a) {
        return reviewed(service.reject(id, CurrentUser.id(a), r.reason(), idempotencyKey));
    }

    private static ResponseEntity<BillingService.Order> reviewed(BillingService.Review review) {
        return ResponseEntity.ok().header("Idempotent-Replayed", String.valueOf(review.replayed())).body(review.order());
    }

    public record PlanRequest(@NotBlank String planCode) {}

    public record ReviewRequest(String note) {}

    public record RejectRequest(@NotBlank String reason) {}

    public record BankRequest(String bankBin, String bankName, String accountNumber, String accountName, String adminEmail, Long expectedVersion) {}

    public record ReceiptRequest(@NotNull Long receivedAmountVnd, String receivedReference, String note) {}

    public record ResolveRequest(@NotBlank String resolution, @NotBlank String note) {}
}
