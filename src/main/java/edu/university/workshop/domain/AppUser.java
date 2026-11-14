package edu.university.workshop.domain;
import jakarta.persistence.*;
import java.util.UUID;

@Entity @Table(name="app_users")
public class AppUser {
    @Id private UUID id;
    @Column(nullable=false, unique=true, length=64) private String username;
    @Column(name="password_hash", nullable=false, length=100) private String passwordHash;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=20) private Role role;
    protected AppUser() { }
    public AppUser(String username, String passwordHash, Role role) {
        this.id=UUID.randomUUID(); this.username=username; this.passwordHash=passwordHash; this.role=role;
    }
    public UUID getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public Role getRole() { return role; }
}
