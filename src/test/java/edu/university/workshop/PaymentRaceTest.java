package edu.university.workshop;

import edu.university.workshop.service.PaymentService;
import edu.university.workshop.service.PortalException;
import edu.university.workshop.service.RegistrationService;
import edu.university.workshop.web.RegistrationForm;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Uses real, separate service transactions; the test itself must not be transactional. */
@SpringBootTest
@ActiveProfiles({"test", "race"})
class PaymentRaceTest {
    @Autowired RegistrationService registrations;
    @Autowired PaymentService payments;

    @Test
    void simultaneousEditAndPaymentWithSameVersionAllowExactlyOneWinner() throws Exception {
        var owner = authenticate("participant1", "PARTICIPANT");
        var created = registrations.create(form("Original Race Learner", 0), owner);
        var id = created.getId();
        long originalVersion = created.getVersion();
        SecurityContextHolder.clearContext();

        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        RaceResult edit;
        RaceResult payment;
        try {
            var editFuture = executor.submit(racing("EDIT", ready, start, actor ->
                    registrations.update(id, form("Changed Race Learner", originalVersion), actor)));
            var paymentFuture = executor.submit(racing("PAYMENT", ready, start, actor ->
                    payments.start(id, originalVersion, actor)));
            assertTrue(ready.await(5, TimeUnit.SECONDS), "Both independent workers must reach the start gate");
            start.countDown();
            edit = editFuture.get(10, TimeUnit.SECONDS);
            payment = paymentFuture.get(10, TimeUnit.SECONDS);
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS), "Race workers must terminate");
        }

        assertNotEquals(edit.succeeded(), payment.succeeded(), "Exactly one request may consume the submitted version");
        RaceResult loser = edit.succeeded() ? payment : edit;
        assertEquals(Integer.valueOf(409), loser.errorStatus(), "The losing request must be a domain conflict");

        try {
            var finalRegistration = registrations.get(id, authenticate("participant1", "PARTICIPANT"));
            assertEquals(originalVersion + 1, finalRegistration.getVersion(), "Only the winning transaction may change the registration version");
            var finance = authenticate("finance", "FINANCE");
            var matchingAttempts = payments.list(finance).stream()
                    .filter(attempt -> attempt.getRegistration().getId().equals(id)).toList();
            if (edit.succeeded()) {
                assertEquals("Changed Race Learner", finalRegistration.getAttendeeName());
                assertEquals("UNPAID", finalRegistration.getPaymentStatus().name());
                assertTrue(matchingAttempts.isEmpty(), "The rejected payment must not leave an attempt behind");
            } else {
                assertEquals("Original Race Learner", finalRegistration.getAttendeeName());
                assertEquals("PENDING", finalRegistration.getPaymentStatus().name());
                assertEquals(1, matchingAttempts.size());
                assertEquals("PENDING", matchingAttempts.get(0).getStatus().name());
            }
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private static Callable<RaceResult> racing(String name, CountDownLatch ready,
                                              CountDownLatch start, AuthenticatedOperation operation) {
        return () -> {
            var actor = authenticate("participant1", "PARTICIPANT");
            try {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Race start gate timed out");
                try {
                    operation.run(actor);
                    return new RaceResult(name, null);
                } catch (PortalException conflict) {
                    return new RaceResult(name, conflict.getStatus());
                }
            } finally {
                SecurityContextHolder.clearContext();
            }
        };
    }

    @FunctionalInterface
    private interface AuthenticatedOperation { void run(Authentication actor); }

    private record RaceResult(String request, Integer errorStatus) {
        boolean succeeded() { return errorStatus == null; }
    }

    private static Authentication authenticate(String username, String role) {
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                username, "unused", List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        return authentication;
    }

    private static RegistrationForm form(String name, long version) {
        var form = new RegistrationForm();
        form.setAttendeeName(name);
        form.setEmail("race@example.invalid");
        form.setMobile("+919000000001");
        form.setOrganisation("Demo University");
        form.setVersion(version);
        return form;
    }
}
