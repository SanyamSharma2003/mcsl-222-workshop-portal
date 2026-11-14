package edu.university.workshop.domain;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="audit_events")
public class AuditEvent {
    @Id private UUID id;
    @Column(nullable=false,updatable=false) private UUID registrationId;
    @Column(nullable=false,length=64,updatable=false) private String actor;
    @Column(nullable=false,length=40,updatable=false) private String action;
    @Column(nullable=false,updatable=false) private Instant occurredAt;
    protected AuditEvent() { }
    public AuditEvent(UUID id,String actor,String action) {
        this.id=UUID.randomUUID(); registrationId=id; this.actor=actor; this.action=action; occurredAt=Instant.now();
    }
    public UUID getId(){return id;} public UUID getRegistrationId(){return registrationId;}
    public String getActor(){return actor;} public String getAction(){return action;}
    public Instant getOccurredAt(){return occurredAt;}
}
