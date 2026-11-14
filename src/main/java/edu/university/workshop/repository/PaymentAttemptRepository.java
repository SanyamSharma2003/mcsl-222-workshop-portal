package edu.university.workshop.repository;
import edu.university.workshop.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt,UUID> {
    List<PaymentAttempt> findAllByOrderByCreatedAtDesc();
    boolean existsByRegistrationIdAndStatus(UUID id, AttemptStatus status);
    @Query("select p.registration.id from PaymentAttempt p where p.id=:id")
    Optional<UUID> findRegistrationId(@Param("id") UUID id);
}
