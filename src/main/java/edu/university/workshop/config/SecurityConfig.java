package edu.university.workshop.config;
import edu.university.workshop.repository.UserRepository;
import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration @EnableMethodSecurity
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder(){return new BCryptPasswordEncoder(12);}
    @Bean UserDetailsService users(UserRepository users){
        return name->users.findByUsername(name).map(u->User.withUsername(u.getUsername()).password(u.getPasswordHash())
            .roles(u.getRole().name()).build()).orElseThrow(()->new UsernameNotFoundException("Invalid credentials"));
    }
    @Bean SecurityFilterChain security(HttpSecurity http,@Value("${app.require-https:false}") boolean requireHttps) throws Exception {
        if(requireHttps)http.requiresChannel(c->c.anyRequest().requiresSecure());
        http.authorizeHttpRequests(a->a
            .dispatcherTypeMatchers(DispatcherType.ERROR,DispatcherType.FORWARD).permitAll()
            .requestMatchers("/","/login","/error","/style.css").permitAll()
            .requestMatchers(HttpMethod.POST,"/gateway/callback").permitAll()
            .requestMatchers("/finance/**").hasAnyRole("FINANCE","ADMIN")
            .requestMatchers("/registrations","/registrations/**").hasAnyRole("PARTICIPANT","ADMIN","FINANCE")
            .anyRequest().denyAll())
            .formLogin(f->f.defaultSuccessUrl("/registrations",true))
            .logout(l->l.logoutSuccessUrl("/login?logout").invalidateHttpSession(true).deleteCookies("JSESSIONID"))
            .csrf(c->c.ignoringRequestMatchers("/gateway/callback"))
            .sessionManagement(s->s.sessionFixation(f->f.migrateSession()))
            .headers(h->h.contentSecurityPolicy(c->c.policyDirectives("default-src 'self'; style-src 'self'; form-action 'self'; frame-ancestors 'none'; base-uri 'none'"))
                .frameOptions(f->f.deny()).httpStrictTransportSecurity(Customizer.withDefaults()));
        return http.build();
    }
}
