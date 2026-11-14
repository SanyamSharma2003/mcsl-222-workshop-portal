package edu.university.workshop.service;
import com.fasterxml.jackson.databind.*;
import edu.university.workshop.domain.*;
import edu.university.workshop.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;

@Service
public class PaymentService {
    private final RegistrationRepository registrations;
    private final PaymentAttemptRepository payments;
    private final AuditRepository audit;
    private final ObjectMapper mapper;
    private final byte[] secret;
    private final boolean demo;
    public PaymentService(RegistrationRepository r,PaymentAttemptRepository p,AuditRepository a,ObjectMapper mapper,
            @Value("${app.gateway-secret}") String secret,@Value("${app.demo-payments:false}") boolean demo){
        registrations=r;payments=p;audit=a;this.mapper=mapper;this.secret=secret.getBytes(StandardCharsets.UTF_8);this.demo=demo;
        if(this.secret.length<32)throw new IllegalArgumentException("Gateway signing secret must contain at least 32 bytes.");
    }
    @PreAuthorize("hasRole('PARTICIPANT')") @Transactional
    public PaymentAttempt start(UUID id,long version,Authentication actor){
        Registration r=RegistrationService.active(registrations.lockById(id));
        RegistrationService.authorize(r,actor,false);RegistrationService.editable(r,version);
        if(payments.existsByRegistrationIdAndStatus(id,AttemptStatus.PENDING))throw new PortalException(409,"A payment is already pending.");
        PaymentAttempt p=payments.save(new PaymentAttempt(r));r.paymentPending();
        audit.save(new AuditEvent(id,actor.getName(),"PAYMENT_STARTED"));registrations.flush();return p;
    }
    @PreAuthorize("hasAnyRole('FINANCE','ADMIN')") @Transactional(readOnly=true)
    public List<PaymentAttempt> list(Authentication actor){return payments.findAllByOrderByCreatedAtDesc();}

    // This is an explicitly labelled lab facility. The production profile disables it.
    @PreAuthorize("hasRole('FINANCE')") @Transactional
    public void demoResolve(UUID id,String outcome,String receipt,Authentication actor){
        if(!demo)throw new PortalException(404,"Payment simulation is disabled.");
        apply(id,null,null,null,outcome,receipt,actor.getName());
    }

    public record GatewayNotice(UUID attemptId,String orderReference,BigDecimal amount,String currency,
                                String outcome,String receipt,Instant issuedAt) { }

    // Authorised by an HMAC over the exact raw JSON body, not by a user's browser return URL.
    @Transactional
    public void webhook(byte[] body,String signature){
        if(body==null||body.length>8192)throw new PortalException(413,"Callback body is too large.");
        if(signature==null||!signature.matches("[0-9a-f]{64}"))throw new PortalException(401,"Invalid gateway signature.");
        try{
            Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret,"HmacSHA256"));
            if(!MessageDigest.isEqual(mac.doFinal(body),HexFormat.of().parseHex(signature)))throw new PortalException(401,"Invalid gateway signature.");
        }catch(GeneralSecurityException e){throw new IllegalStateException("Gateway verification unavailable.",e);}
        GatewayNotice n;
        try{n=mapper.readerFor(GatewayNotice.class).with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(body);}
        catch(Exception e){throw new PortalException(400,"Malformed gateway event.");}
        Instant now=Instant.now();
        if(n==null||n.attemptId()==null||n.orderReference()==null||n.amount()==null||n.currency()==null||n.issuedAt()==null
                ||n.issuedAt().isBefore(now.minusSeconds(300))||n.issuedAt().isAfter(now.plusSeconds(30)))
            throw new PortalException(400,"Missing, expired or invalid gateway event fields.");
        apply(n.attemptId(),n.orderReference(),n.amount(),n.currency(),n.outcome(),n.receipt(),"gateway");
    }

    private void apply(UUID id,String order,BigDecimal amount,String currency,String outcome,String receipt,String actor){
        AttemptStatus target;
        try{target=AttemptStatus.valueOf(outcome);}catch(Exception e){throw new PortalException(400,"Outcome must be SETTLED or FAILED.");}
        if(target==AttemptStatus.PENDING||receipt==null||!receipt.matches("[A-Za-z0-9._:-]{8,100}"))
            throw new PortalException(400,"A valid terminal outcome and unique receipt reference are required.");
        // Read only the parent identifier first; then always lock registration BEFORE loading payment state.
        UUID registrationId=payments.findRegistrationId(id).orElseThrow(()->new PortalException(404,"Payment attempt not found."));
        Registration r=RegistrationService.active(registrations.lockById(registrationId));
        PaymentAttempt p=payments.findById(id).orElseThrow(()->new PortalException(404,"Payment attempt not found."));
        if(order!=null&&(!p.getOrderReference().equals(order)||p.getAmount().compareTo(amount)!=0||!p.getCurrency().equals(currency)))
            throw new PortalException(400,"Order, amount or currency does not match the stored payment.");
        if(p.getStatus()!=AttemptStatus.PENDING){
            if(p.getStatus()==target&&receipt.equals(p.getReceipt()))return;
            throw new PortalException(409,"Payment already has a different final result.");
        }
        if(r.getPaymentStatus()!=PaymentStatus.PENDING)throw new PortalException(409,"Registration is not awaiting this payment.");
        p.resolve(target,receipt);
        if(target==AttemptStatus.SETTLED)r.paymentSettled();else r.paymentFailed();
        audit.save(new AuditEvent(r.getId(),actor,"PAYMENT_"+target.name()));registrations.flush();payments.flush();
    }
}
