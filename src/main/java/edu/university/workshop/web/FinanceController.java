package edu.university.workshop.web;

import edu.university.workshop.service.PaymentService;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@PreAuthorize("hasAnyRole('FINANCE','ADMIN')")
public class FinanceController {
    private final PaymentService payments;
    private final boolean demoPayments;

    public FinanceController(PaymentService payments, @Value("${app.demo-payments:false}") boolean demoPayments) {
        this.payments = payments;
        this.demoPayments = demoPayments;
    }

    @GetMapping("/finance/payments")
    public String payments(Authentication authentication, Model model) {
        model.addAttribute("payments", payments.list(authentication));
        model.addAttribute("demoPayments", demoPayments);
        return "finance/payments";
    }

    @PostMapping("/finance/payments/{id}/resolve")
    @PreAuthorize("hasRole('FINANCE')")
    public String resolve(@PathVariable UUID id, @RequestParam String outcome,
                          @RequestParam(defaultValue = "") String receipt,
                          Authentication authentication, RedirectAttributes redirect) {
        payments.demoResolve(id, outcome, receipt, authentication);
        redirect.addFlashAttribute("success", "Demo payment result recorded. Registration status has been updated in the same transaction.");
        return "redirect:/finance/payments";
    }
}
