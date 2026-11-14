package edu.university.workshop.repository;
import edu.university.workshop.domain.Registration;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface RegistrationRepository extends JpaRepository<Registration,UUID> {
    List<Registration> findByDeletedAtIsNullOrderByCreatedAtDesc();
    List<Registration> findByOwnerUsernameAndDeletedAtIsNullOrderByCreatedAtDesc(String username);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Registration r where r.id=:id")
    Optional<Registration> lockById(@Param("id") UUID id);
}
