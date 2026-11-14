package edu.university.workshop;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.university.workshop.domain.PaymentAttempt;
import edu.university.workshop.service.PaymentService;
import edu.university.workshop.service.PortalException;
import edu.university.workshop.service.RegistrationService;
import edu.university.workshop.web.RegistrationForm;
import jakarta.persistence.EntityManager;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

/** Integration checks for authorization and the registration/payment boundary. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PortalIntegrationTest {
    @Autowired RegistrationService registrations;
    @Autowired PaymentService payments;
    @Autowired EntityManager entityManager;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @Test
    void participantCanCreateReadUpdateAndDeleteAnUnpaidRegistration() {
        var owner = as("participant1", "PARTICIPANT");
        var registration = registrations.create(form("Asha Learner", 0), owner);
        entityManager.flush();

        assertEquals("Asha Learner", registrations.get(registration.getId(), owner).getAttendeeName());
        assertEquals("UNPAID", registration.getPaymentStatus().name());
        assertTrue(registrations.list(owner).stream().anyMatch(r -> r.getId().equals(registration.getId())));

        registrations.update(registration.getId(), form("Asha Updated", registration.getVersion()), owner);
        entityManager.flush();
        var updated = registrations.get(registration.getId(), owner);
        assertEquals("Asha Updated", updated.getAttendeeName());

        registrations.delete(updated.getId(), updated.getVersion(), owner);
        entityManager.flush();
        assertTrue(registrations.list(owner).stream().noneMatch(r -> r.getId().equals(updated.getId())));
        assertPortalStatus(404, () -> registrations.get(updated.getId(), owner));
    }

    @Test
    void anotherParticipantCannotReadChangeDeleteOrPayForTheRegistration() {
        var owner = as("participant1", "PARTICIPANT");
        var registration = registrations.create(form("Private Learner", 0), owner);
        entityManager.flush();
        var other = as("participant2", "PARTICIPANT");

        assertTrue(registrations.list(other).stream().noneMatch(r -> r.getId().equals(registration.getId())));
        assertThrows(AccessDeniedException.class, () -> registrations.get(registration.getId(), other));
        assertThrows(AccessDeniedException.class, () -> registrations.update(registration.getId(), form("Intruder", registration.getVersion()), other));
        assertThrows(AccessDeniedException.class, () -> registrations.delete(registration.getId(), registration.getVersion(), other));
        assertThrows(AccessDeniedException.class, () -> payments.start(registration.getId(), registration.getVersion(), other));

        owner = as("participant1", "PARTICIPANT");
        assertEquals("Private Learner", registrations.get(registration.getId(), owner).getAttendeeName());
        assertEquals("UNPAID", registrations.get(registration.getId(), owner).getPaymentStatus().name());
    }

    @Test
    void administratorCanManageAnUnpaidRegistrationAcrossOwners() {
        var owner = as("participant1", "PARTICIPANT");
        var registration = registrations.create(form("Original Learner", 0), owner);
        entityManager.flush();
        var admin = as("admin", "ADMIN");

        assertTrue(registrations.list(admin).stream().anyMatch(r -> r.getId().equals(registration.getId())));
        assertEquals("Original Learner", registrations.get(registration.getId(), admin).getAttendeeName());
        registrations.update(registration.getId(), form("Admin Correction", registration.getVersion()), admin);
        entityManager.flush();
        var corrected = registrations.get(registration.getId(), admin);
        assertEquals("Admin Correction", corrected.getAttendeeName());
        registrations.delete(corrected.getId(), corrected.getVersion(), admin);
        assertPortalStatus(404, () -> registrations.get(corrected.getId(), admin));
    }

    @Test
    void pendingPaymentLocksRegistrationForBothParticipantAndAdministrator() {
        var owner = as("participant1", "PARTICIPANT");
        var registration = registrations.create(form("Pending Learner", 0), owner);
        entityManager.flush();
        var attempt = payments.start(registration.getId(), registration.getVersion(), owner);
        entityManager.flush();
        var pending = registrations.get(registration.getId(), owner);

        assertEquals("PENDING", pending.getPaymentStatus().name());
        assertEquals("PENDING", attempt.getStatus().name());
        assertTrue(attempt.getAmount().compareTo(BigDecimal.ZERO) > 0);
        assertEquals("INR", attempt.getCurrency());
        assertPortalStatus(409, () -> registrations.update(pending.getId(), form("Blocked", pending.getVersion()), owner));
        assertPortalStatus(409, () -> registrations.delete(pending.getId(), pending.getVersion(), owner));
        assertPortalStatus(409, () -> payments.start(pending.getId(), pending.getVersion(), owner));

        var admin = as("admin", "ADMIN");
        assertPortalStatus(409, () -> registrations.update(pending.getId(), form("Admin Blocked", pending.getVersion()), admin));
        assertPortalStatus(409, () -> registrations.delete(pending.getId(), pending.getVersion(), admin));
    }

    @Test
    void settlementIsIdempotentAndPaidRowsCannotBeChangedEvenByAdministrator() {
        var owner = as("participant1", "PARTICIPANT");
        var registration = registrations.create(form("Paid Learner", 0), owner);
        entityManager.flush();
        var attempt = payments.start(registration.getId(), registration.getVersion(), owner);
        var finance = as("finance", "FINANCE");
        var attemptCount = payments.list(finance).size();

        payments.demoResolve(attempt.getId(), "SETTLED", "RECEIPT-TEST-001", finance);
        payments.demoResolve(attempt.getId(), "SETTLED", "RECEIPT-TEST-001", finance);
        entityManager.flush();
        assertEquals(attemptCount, payments.list(finance).size(), "A repeated settlement must not create another payment");
        assertEquals("SETTLED", attempt.getStatus().name());
        assertPortalStatus(409, () -> payments.demoResolve(attempt.getId(), "FAILED", "RECEIPT-TEST-001", finance));

        owner = as("participant1", "PARTICIPANT");
        var paid = registrations.get(registration.getId(), owner);
        assertEquals("PAID", paid.getPaymentStatus().name());
        var participant = owner;
        assertPortalStatus(409, () -> registrations.update(paid.getId(), form("Blocked", paid.getVersion()), participant));
        assertPortalStatus(409, () -> registrations.delete(paid.getId(), paid.getVersion(), participant));

        var admin = as("admin", "ADMIN");
        assertPortalStatus(409, () -> registrations.update(paid.getId(), form("Admin Blocked", paid.getVersion()), admin));
        assertPortalStatus(409, () -> registrations.delete(paid.getId(), paid.getVersion(), admin));
    }

    @Test
    void failedPaymentAllowsCorrectionAndCreatesANewAttemptOnRetry() {
        var owner = as("participant1", "PARTICIPANT");
        var registration = registrations.create(form("Retry Learner", 0), owner);
        entityManager.flush();
        var first = payments.start(registration.getId(), registration.getVersion(), owner);
        var finance = as("finance", "FINANCE");
        payments.demoResolve(first.getId(), "FAILED", "FAIL-TEST-001", finance);
        entityManager.flush();

        owner = as("participant1", "PARTICIPANT");
        var unpaid = registrations.get(registration.getId(), owner);
        assertEquals("UNPAID", unpaid.getPaymentStatus().name());
        assertEquals("FAILED", first.getStatus().name());
        registrations.update(unpaid.getId(), form("Corrected Learner", unpaid.getVersion()), owner);
        entityManager.flush();
        var corrected = registrations.get(unpaid.getId(), owner);
        var retry = payments.start(corrected.getId(), corrected.getVersion(), owner);
        assertNotEquals(first.getId(), retry.getId());
        assertNotEquals(first.getOrderReference(), retry.getOrderReference());
        assertEquals("PENDING", retry.getStatus().name());
    }

    @Test
    void financeHasReadOnlyRegistrationAccessAndAdministratorCannotSettlePayments() {
        var owner = as("participant1", "PARTICIPANT");
        var registration = registrations.create(form("Separated Duties", 0), owner);
        entityManager.flush();
        var finance = as("finance", "FINANCE");
        assertThrows(AccessDeniedException.class, () -> registrations.create(form("Forbidden", 0), finance));
        assertEquals("Separated Duties", registrations.get(registration.getId(), finance).getAttendeeName());
        assertThrows(AccessDeniedException.class, () -> registrations.update(registration.getId(), form("Forbidden", registration.getVersion()), finance));
        assertThrows(AccessDeniedException.class, () -> registrations.delete(registration.getId(), registration.getVersion(), finance));

        owner = as("participant1", "PARTICIPANT");
        var attempt = payments.start(registration.getId(), registration.getVersion(), owner);
        var admin = as("admin", "ADMIN");
        assertThrows(AccessDeniedException.class, () -> payments.demoResolve(attempt.getId(), "SETTLED", "RECEIPT-TEST-002", admin));
    }

    @Test
    void staleVersionCannotOverwriteOrDeleteANewerRegistration() {
        var owner = as("participant1", "PARTICIPANT");
        var registration = registrations.create(form("First Version", 0), owner);
        entityManager.flush();
        long oldVersion = registration.getVersion();
        registrations.update(registration.getId(), form("Newer Version", oldVersion), owner);
        entityManager.flush();

        assertPortalStatus(409, () -> registrations.update(registration.getId(), form("Stale Version", oldVersion), owner));
        assertPortalStatus(409, () -> registrations.delete(registration.getId(), oldVersion, owner));
        assertEquals("Newer Version", registrations.get(registration.getId(), owner).getAttendeeName());
    }

    @Test
    void serviceRejectsInvalidRegistrationInput() {
        var owner = as("participant1", "PARTICIPANT");
        var invalid = form("", 0);
        invalid.setEmail("not-an-email");
        invalid.setMobile("abc");
        assertThrows(ConstraintViolationException.class, () -> registrations.create(invalid, owner));
        assertTrue(registrations.list(owner).isEmpty());
    }

    @Test
    void unauthenticatedVisitorMustLogIn() throws Exception {
        mvc.perform(get("/registrations")).andExpect(status().is3xxRedirection());
    }

    @Test
    void logoutRequiresCsrfInvalidatesSessionAndReturnsAWorkingLoginPage() throws Exception {
        var login = mvc.perform(post("/login").with(csrf())
                .param("username", "participant1").param("password", "LabDemo!2026"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/registrations"))
                .andReturn();
        var session = (MockHttpSession) login.getRequest().getSession(false);
        assertNotNull(session);
        mvc.perform(get("/registrations").session(session)).andExpect(status().isOk());

        mvc.perform(post("/logout").session(session)).andExpect(status().isForbidden());
        assertFalse(session.isInvalid(), "A request without CSRF must not end the authenticated session");
        mvc.perform(get("/registrations").session(session)).andExpect(status().isOk());

        mvc.perform(post("/logout").session(session).with(csrf()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
        assertTrue(session.isInvalid(), "Successful logout must invalidate the old authenticated session");
        mvc.perform(get("/login").with(anonymous())).andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"username\"")))
                .andExpect(content().string(containsString("name=\"_csrf\"")));
        mvc.perform(get("/registrations").with(anonymous())).andExpect(status().is3xxRedirection());
    }

    @Test
    @WithMockUser(username = "participant1", roles = "PARTICIPANT")
    void stateChangingRequestWithoutCsrfIsRejected() throws Exception {
        mvc.perform(post("/registrations")
                .param("attendeeName", "CSRF Attempt")
                .param("email", "csrf@example.invalid")
                .param("mobile", "+919000000001")
                .param("organisation", "Demo University"))
                .andExpect(status().isForbidden());
        assertTrue(registrations.list(as("participant1", "PARTICIPANT")).isEmpty());
    }

    @Test
    @WithMockUser(username = "participant1", roles = "PARTICIPANT")
    void submittedOwnerAndPaymentFieldsCannotBeMassAssigned() throws Exception {
        mvc.perform(post("/registrations").with(csrf())
                .param("attendeeName", "DTO Learner")
                .param("email", "dto@example.invalid")
                .param("mobile", "+919000000001")
                .param("organisation", "Demo University")
                .param("version", "0")
                .param("owner", "participant2")
                .param("ownerId", "2")
                .param("paymentStatus", "PAID")
                .param("fee", "0.01")
                .param("role", "ADMIN"))
                .andExpect(status().is3xxRedirection());

        var owner = as("participant1", "PARTICIPANT");
        var owned = registrations.list(owner);
        assertEquals(1, owned.size());
        var registration = owned.get(0);
        assertEquals("DTO Learner", registration.getAttendeeName());
        assertEquals("UNPAID", registration.getPaymentStatus().name());
        var attempt = payments.start(registration.getId(), registration.getVersion(), owner);
        assertTrue(attempt.getAmount().compareTo(new BigDecimal("0.01")) > 0);
        assertTrue(registrations.list(as("participant2", "PARTICIPANT")).isEmpty());
    }

    @Test
    void registrationAndFinancePagesRenderTheirActualThymeleafTemplates() throws Exception {
        var owner = as("participant1", "PARTICIPANT");
        var registration = registrations.create(form("Rendering Learner", 0), owner);
        entityManager.flush();

        mvc.perform(get("/registrations").with(user("participant1").roles("PARTICIPANT")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Rendering Learner")));
        mvc.perform(get("/registrations/new").with(user("participant1").roles("PARTICIPANT")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("New registration")))
                .andExpect(content().string(containsString("name=\"_csrf\"")));
        mvc.perform(get("/registrations/{id}", registration.getId()).with(user("participant1").roles("PARTICIPANT")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Registration details")))
                .andExpect(content().string(containsString("Create payment order")));
        mvc.perform(get("/registrations/{id}/edit", registration.getId()).with(user("participant1").roles("PARTICIPANT")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Edit registration")))
                .andExpect(content().string(containsString("Rendering Learner")));

        var attempt = payments.start(registration.getId(), registration.getVersion(), as("participant1", "PARTICIPANT"));
        mvc.perform(get("/finance/payments").with(user("finance").roles("FINANCE")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Payment desk")))
                .andExpect(content().string(containsString(attempt.getOrderReference())));
        mvc.perform(get("/registrations/{id}", registration.getId()).with(user("participant1").roles("PARTICIPANT")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Payment is pending")));
    }

    @Test
    void invalidGatewaySignatureCannotSettleAPayment() throws Exception {
        var owner = as("participant1", "PARTICIPANT");
        var registration = registrations.create(form("Signature Learner", 0), owner);
        var attempt = payments.start(registration.getId(), registration.getVersion(), owner);
        byte[] body = gatewayBody(attempt, attempt.getAmount(), "SETTLED", "GATEWAY-TEST-001", Instant.now());

        mvc.perform(post("/gateway/callback").with(anonymous()).contentType("application/json")
                .header("X-Payment-Signature", "0".repeat(64)).content(body))
                .andExpect(status().isUnauthorized());
        byte[] nullNotice = "null".getBytes(StandardCharsets.UTF_8);
        mvc.perform(post("/gateway/callback").with(anonymous()).contentType("application/json")
                .header("X-Payment-Signature", sign(nullNotice)).content(nullNotice))
                .andExpect(status().isBadRequest());
        assertEquals("PENDING", attempt.getStatus().name());
        assertEquals("PENDING", registrations.get(registration.getId(), as("participant1", "PARTICIPANT")).getPaymentStatus().name());
    }

    @Test
    void signedGatewayEventMustMatchStoredAmount() throws Exception {
        var owner = as("participant1", "PARTICIPANT");
        var registration = registrations.create(form("Amount Learner", 0), owner);
        var attempt = payments.start(registration.getId(), registration.getVersion(), owner);
        byte[] body = gatewayBody(attempt, attempt.getAmount().subtract(BigDecimal.ONE), "SETTLED", "GATEWAY-TEST-002", Instant.now());

        mvc.perform(post("/gateway/callback").with(anonymous()).contentType("application/json")
                .header("X-Payment-Signature", sign(body)).content(body))
                .andExpect(status().isBadRequest());
        assertEquals("PENDING", attempt.getStatus().name());
        assertEquals("PENDING", registrations.get(registration.getId(), as("participant1", "PARTICIPANT")).getPaymentStatus().name());
    }

    @Test
    void signedGatewayEventsOutsideFreshnessWindowAreRejected() throws Exception {
        var owner = as("participant1", "PARTICIPANT");
        var registration = registrations.create(form("Timestamp Learner", 0), owner);
        var attempt = payments.start(registration.getId(), registration.getVersion(), owner);
        byte[] expired = gatewayBody(attempt, attempt.getAmount(), "SETTLED", "GATEWAY-TEST-003", Instant.now().minusSeconds(600));
        byte[] future = gatewayBody(attempt, attempt.getAmount(), "SETTLED", "GATEWAY-TEST-003", Instant.now().plusSeconds(120));

        for (byte[] body : List.of(expired, future)) {
            mvc.perform(post("/gateway/callback").with(anonymous()).contentType("application/json")
                    .header("X-Payment-Signature", sign(body)).content(body))
                    .andExpect(status().isBadRequest());
        }
        assertEquals("PENDING", attempt.getStatus().name());
    }

    @Test
    void anonymousSignedCallbackSettlesIdempotentlyAndRejectsConflictingOutcome() throws Exception {
        var owner = as("participant1", "PARTICIPANT");
        var registration = registrations.create(form("Gateway Learner", 0), owner);
        var attempt = payments.start(registration.getId(), registration.getVersion(), owner);
        byte[] settled = gatewayBody(attempt, attempt.getAmount(), "SETTLED", "GATEWAY-TEST-004", Instant.now());

        // No browser authentication and no CSRF token: the exact-body HMAC authenticates this endpoint.
        for (int replay = 0; replay < 2; replay++) {
            mvc.perform(post("/gateway/callback").with(anonymous()).contentType("application/json")
                    .header("X-Payment-Signature", sign(settled)).content(settled))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("accepted"));
        }
        assertEquals("SETTLED", attempt.getStatus().name());
        assertEquals("PAID", registrations.get(registration.getId(), as("participant1", "PARTICIPANT")).getPaymentStatus().name());

        byte[] conflicting = gatewayBody(attempt, attempt.getAmount(), "FAILED", "GATEWAY-TEST-004", Instant.now());
        mvc.perform(post("/gateway/callback").with(anonymous()).contentType("application/json")
                .header("X-Payment-Signature", sign(conflicting)).content(conflicting))
                .andExpect(status().isConflict());
        assertEquals("SETTLED", attempt.getStatus().name());
    }

    private byte[] gatewayBody(PaymentAttempt attempt, BigDecimal amount, String outcome, String receipt, Instant issuedAt) throws Exception {
        return mapper.writeValueAsBytes(new PaymentService.GatewayNotice(attempt.getId(), attempt.getOrderReference(),
                amount, attempt.getCurrency(), outcome, receipt, issuedAt));
    }

    private static String sign(byte[] body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("TestGatewaySigningSecretOnly2026ChangeMe".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(body));
    }

    private static RegistrationForm form(String name, long version) {
        var form = new RegistrationForm();
        form.setAttendeeName(name);
        form.setEmail("learner@example.invalid");
        form.setMobile("+919000000001");
        form.setOrganisation("Demo University");
        form.setVersion(version);
        return form;
    }

    private static Authentication as(String username, String role) {
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                username, "unused", List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        return authentication;
    }

    private static void assertPortalStatus(int expected, Runnable operation) {
        var error = assertThrows(PortalException.class, operation::run);
        assertEquals(expected, error.getStatus());
    }
}
