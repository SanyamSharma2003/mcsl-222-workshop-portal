package edu.university.workshop.web;

import edu.university.workshop.domain.Registration;
import edu.university.workshop.service.PaymentService;
import edu.university.workshop.service.RegistrationService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class RegistrationController {
    private final RegistrationService registrations;
    private final PaymentService payments;

    public RegistrationController(RegistrationService registrations, PaymentService payments) {
        this.registrations = registrations;
        this.payments = payments;
    }

    @InitBinder("form")
    public void bindAttendeeFieldsOnly(WebDataBinder binder) {
        binder.setAllowedFields("attendeeName", "email", "mobile", "organisation", "version");
    }

    @GetMapping("/")
    public String home() {
        return "redirect:/registrations";
    }

    @GetMapping("/registrations")
    public String list(Authentication authentication, Model model) {
        model.addAttribute("registrations", registrations.list(authentication));
        return "registrations/list";
    }

    @PreAuthorize("hasAnyRole('PARTICIPANT', 'ADMIN')")
    @GetMapping("/registrations/new")
    public String newRegistration(Model model) {
        model.addAttribute("form", new RegistrationForm());
        model.addAttribute("editing", false);
        return "registrations/form";
    }

    @PreAuthorize("hasAnyRole('PARTICIPANT', 'ADMIN')")
    @PostMapping("/registrations")
    public String create(@Valid @ModelAttribute("form") RegistrationForm form,
                         BindingResult errors, Authentication authentication,
                         Model model, RedirectAttributes redirect) {
        if (errors.hasErrors()) {
            model.addAttribute("editing", false);
            return "registrations/form";
        }
        Registration saved = registrations.create(form, authentication);
        redirect.addFlashAttribute("success", "Registration created. Payment is not yet settled.");
        return "redirect:/registrations/" + saved.getId();
    }

    @GetMapping("/registrations/{id}")
    public String detail(@PathVariable UUID id, Authentication authentication, Model model) {
        model.addAttribute("registration", registrations.get(id, authentication));
        return "registrations/detail";
    }

    @PreAuthorize("hasAnyRole('PARTICIPANT', 'ADMIN')")
    @GetMapping("/registrations/{id}/edit")
    public String edit(@PathVariable UUID id, Authentication authentication, Model model) {
        Registration registration = registrations.get(id, authentication);
        RegistrationForm form = new RegistrationForm();
        form.setAttendeeName(registration.getAttendeeName());
        form.setEmail(registration.getEmail());
        form.setMobile(registration.getMobile());
        form.setOrganisation(registration.getOrganisation());
        form.setVersion(registration.getVersion());
        model.addAttribute("form", form);
        model.addAttribute("editing", true);
        model.addAttribute("registrationId", id);
        model.addAttribute("paymentLocked", !registration.getPaymentStatus().name().equals("UNPAID"));
        return "registrations/form";
    }

    @PreAuthorize("hasAnyRole('PARTICIPANT', 'ADMIN')")
    @PostMapping("/registrations/{id}/edit")
    public String update(@PathVariable UUID id,
                         @Valid @ModelAttribute("form") RegistrationForm form,
                         BindingResult errors, Authentication authentication,
                         Model model, RedirectAttributes redirect) {
        // Authorize the target even when submitted fields contain validation errors.
        Registration existing = registrations.get(id, authentication);
        if (errors.hasErrors()) {
            model.addAttribute("editing", true);
            model.addAttribute("registrationId", id);
            model.addAttribute("paymentLocked", !existing.getPaymentStatus().name().equals("UNPAID"));
            return "registrations/form";
        }
        registrations.update(id, form, authentication);
        redirect.addFlashAttribute("success", "Registration details updated.");
        return "redirect:/registrations/" + id;
    }

    @PreAuthorize("hasAnyRole('PARTICIPANT', 'ADMIN')")
    @PostMapping("/registrations/{id}/delete")
    public String delete(@PathVariable UUID id, @RequestParam long version,
                         Authentication authentication, RedirectAttributes redirect) {
        registrations.delete(id, version, authentication);
        redirect.addFlashAttribute("success", "Unpaid registration deleted. Its audit record is retained.");
        return "redirect:/registrations";
    }

    @PreAuthorize("hasRole('PARTICIPANT')")
    @PostMapping("/registrations/{id}/pay")
    public String pay(@PathVariable UUID id, @RequestParam long version,
                      Authentication authentication, RedirectAttributes redirect) {
        payments.start(id, version, authentication);
        redirect.addFlashAttribute("success", "A payment order was created. The registration is locked while payment is pending.");
        return "redirect:/registrations/" + id;
    }
}
