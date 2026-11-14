package edu.university.workshop.domain;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="workshop_registrations")
public class Registration {
    @Id private UUID id;
    @ManyToOne(fetch=FetchType.EAGER, optional=false) @JoinColumn(name="owner_id", updatable=false) private AppUser owner;
    @Column(nullable=false, length=100) private String attendeeName;
    @Column(nullable=false, length=254) private String email;
    @Column(nullable=false, length=13) private String mobile;
    @Column(nullable=false, length=150) private String organisation;
    @Column(nullable=false, precision=10, scale=2, updatable=false) private BigDecimal amountDue;
    @Column(nullable=false, length=3, updatable=false) private String currency;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=20) private PaymentStatus paymentStatus;
    @Version private long version;
    @Column(nullable=false, updatable=false) private Instant createdAt;
    @Column(nullable=false) private Instant updatedAt;
    private Instant deletedAt;
    protected Registration() { }
    public Registration(AppUser owner, String name, String email, String mobile, String organisation) {
        id=UUID.randomUUID(); this.owner=owner; amountDue=new BigDecimal("2500.00"); currency="INR";
        paymentStatus=PaymentStatus.UNPAID; createdAt=Instant.now();
        changeDetails(name,email,mobile,organisation);
    }
    public void changeDetails(String name,String email,String mobile,String organisation) {
        this.attendeeName=name.strip(); this.email=email.strip(); this.mobile=mobile.strip();
        this.organisation=organisation.strip(); updatedAt=Instant.now();
    }
    public void paymentPending() { paymentStatus=PaymentStatus.PENDING; updatedAt=Instant.now(); }
    public void paymentSettled() { paymentStatus=PaymentStatus.PAID; updatedAt=Instant.now(); }
    public void paymentFailed() { paymentStatus=PaymentStatus.UNPAID; updatedAt=Instant.now(); }
    public void markDeleted() { deletedAt=Instant.now(); updatedAt=deletedAt; }
    public UUID getId(){return id;} public AppUser getOwner(){return owner;}
    public String getAttendeeName(){return attendeeName;} public String getEmail(){return email;}
    public String getMobile(){return mobile;} public String getOrganisation(){return organisation;}
    public BigDecimal getAmountDue(){return amountDue;} public String getCurrency(){return currency;}
    public PaymentStatus getPaymentStatus(){return paymentStatus;} public long getVersion(){return version;}
    public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;}
    public Instant getDeletedAt(){return deletedAt;}
}
