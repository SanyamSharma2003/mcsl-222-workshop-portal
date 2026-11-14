package edu.university.workshop.config;
import edu.university.workshop.domain.*;
import edu.university.workshop.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration @Profile({"demo","test"})
public class DemoData {
    @Bean CommandLineRunner demoAccounts(UserRepository users,PasswordEncoder encoder){return args->{
        for(String name:new String[]{"participant1","participant2","admin","finance"}){
            if(users.findByUsername(name).isPresent())continue;
            Role role=name.equals("admin")?Role.ADMIN:name.equals("finance")?Role.FINANCE:Role.PARTICIPANT;
            users.save(new AppUser(name,encoder.encode("LabDemo!2026"),role));
        }
    };}
}
