package com.company.bds.billing;
import com.company.bds.shared.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List; import java.util.UUID;
@RestController @RequestMapping("/api/v1/billing")
public class BillingController {
 private final BillingService service; public BillingController(BillingService s){service=s;}
 @GetMapping("/plans") public List<BillingService.Plan> plans(){return service.plans();}
 @GetMapping("/orders") public List<BillingService.Order> mine(Authentication a){return service.mine(CurrentUser.id(a));}
 @PostMapping("/orders") public BillingService.Order create(@Valid @RequestBody PlanRequest r,Authentication a){return service.create(CurrentUser.id(a),r.planCode());}
 @PostMapping("/orders/{id}/reported") public BillingService.Order report(@PathVariable UUID id,Authentication a){return service.report(id,CurrentUser.id(a));}
 @PostMapping("/orders/{id}/cancel") public BillingService.Order cancel(@PathVariable UUID id,Authentication a){return service.cancel(id,CurrentUser.id(a));}
 @GetMapping("/admin/bank") public BillingService.BankSettings bank(){return service.bank();}
 @PutMapping("/admin/bank") public BillingService.BankSettings bank(@RequestBody BillingService.BankSettings b){return service.saveBank(b);}
 @GetMapping("/admin/reconciliation") public List<BillingService.Order> queue(){return service.queue();}
 @PostMapping("/admin/reconciliation/{id}/approve") public BillingService.Order approve(@PathVariable UUID id,@RequestBody(required=false) ReviewRequest r,Authentication a){return service.approve(id,CurrentUser.id(a),r==null?null:r.note());}
 @PostMapping("/admin/reconciliation/{id}/reject") public BillingService.Order reject(@PathVariable UUID id,@Valid @RequestBody RejectRequest r,Authentication a){return service.reject(id,CurrentUser.id(a),r.reason());}
 public record PlanRequest(@NotBlank String planCode){} public record ReviewRequest(String note){} public record RejectRequest(@NotBlank String reason){}
}
