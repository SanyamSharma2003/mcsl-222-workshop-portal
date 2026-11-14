package edu.university.workshop.domain;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="payment_attempts")
public class PaymentAttempt {
    @Id private UUID id;
    @ManyToOne(fetch=FetchType.EAGER,optional=false) @JoinColumn(name="registration_id",updatable=false) private Registration registration;
    @Column(nullable=false,unique=true,length=40,updatable=false) private String orderReference;
    @Column(nullable=false,precision=10,scale=2,updatable=false) private BigDecimal amount;
    @Column(nullable=false,length=3,updatable=false) private String currency;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private AttemptStatus status;
    @Column(unique=true,length=100) private String receipt;
    @Column(nullable=false,updatable=false) private Instant createdAt;
    private Instant resolvedAt;
    protected PaymentAttempt() { }
    public PaymentAttempt(Registration registration) {
        id=UUID.randomUUID(); this.registration=registration; orderReference="WT-"+UUID.randomUUID();
        amount=registration.getAmountDue(); currency=registration.getCurrency(); status=AttemptStatus.PENDING;
        createdAt=Instant.now();
    }
    public void resolve(AttemptStatus outcome,String receipt) { this.status=outcome; this.receipt=receipt; resolvedAt=Instant.now(); }
    public UUID getId(){return id;} public Registration getRegistration(){return registration;}
    public String getOrderReference(){return orderReference;} public BigDecimal getAmount(){return amount;}
    public String getCurrency(){return currency;} public AttemptStatus getStatus(){return status;}
    public String getReceipt(){return receipt;} public Instant getCreatedAt(){return createdAt;}
    public Instant getResolvedAt(){return resolvedAt;}
}
