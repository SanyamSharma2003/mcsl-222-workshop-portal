package edu.university.workshop.repository;
import edu.university.workshop.domain.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface AuditRepository extends JpaRepository<AuditEvent,UUID> { }
