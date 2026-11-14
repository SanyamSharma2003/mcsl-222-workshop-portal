package edu.university.workshop.repository;
import edu.university.workshop.domain.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface UserRepository extends JpaRepository<AppUser,UUID> { Optional<AppUser> findByUsername(String username); }
