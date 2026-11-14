package edu.university.workshop.service;
import edu.university.workshop.domain.*;
import edu.university.workshop.repository.*;
import edu.university.workshop.web.RegistrationForm;
import jakarta.validation.Valid;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import java.util.*;

@Service @Validated
public class RegistrationService {
    private final RegistrationRepository registrations;
    private final UserRepository users;
    private final AuditRepository audit;
    public RegistrationService(RegistrationRepository r,UserRepository u,AuditRepository a){registrations=r;users=u;audit=a;}
    public static boolean role(Authentication a,String role){return a!=null&&a.getAuthorities().stream().anyMatch(x->x.getAuthority().equals("ROLE_"+role));}
    @PreAuthorize("hasAnyRole('PARTICIPANT','ADMIN','FINANCE')") @Transactional(readOnly=true)
    public List<Registration> list(Authentication actor){
        return role(actor,"ADMIN")||role(actor,"FINANCE") ? registrations.findByDeletedAtIsNullOrderByCreatedAtDesc()
            : registrations.findByOwnerUsernameAndDeletedAtIsNullOrderByCreatedAtDesc(actor.getName());
    }
    @PreAuthorize("hasAnyRole('PARTICIPANT','ADMIN','FINANCE')") @Transactional(readOnly=true)
    public Registration get(UUID id,Authentication actor){
        Registration r=active(registrations.findById(id)); authorize(r,actor,true);return r;
    }
    @PreAuthorize("hasAnyRole('PARTICIPANT','ADMIN')") @Transactional
    public Registration create(@Valid RegistrationForm f,Authentication actor){
        AppUser owner=users.findByUsername(actor.getName()).orElseThrow(()->new AccessDeniedException("Account unavailable"));
        Registration r=registrations.save(new Registration(owner,f.getAttendeeName(),f.getEmail(),f.getMobile(),f.getOrganisation()));
        audit.save(new AuditEvent(r.getId(),actor.getName(),"CREATED"));return r;
    }
    @PreAuthorize("hasAnyRole('PARTICIPANT','ADMIN')") @Transactional
    public Registration update(UUID id,@Valid RegistrationForm f,Authentication actor){
        Registration r=active(registrations.lockById(id)); authorize(r,actor,false); editable(r,f.getVersion());
        r.changeDetails(f.getAttendeeName(),f.getEmail(),f.getMobile(),f.getOrganisation());
        audit.save(new AuditEvent(id,actor.getName(),"UPDATED"));registrations.flush();return r;
    }
    @PreAuthorize("hasAnyRole('PARTICIPANT','ADMIN')") @Transactional
    public void delete(UUID id,long version,Authentication actor){
        Registration r=active(registrations.lockById(id));authorize(r,actor,false);editable(r,version);
        r.markDeleted();audit.save(new AuditEvent(id,actor.getName(),"DELETED"));registrations.flush();
    }
    static Registration active(Optional<Registration> result){
        Registration r=result.orElseThrow(()->new PortalException(404,"Registration not found."));
        if(r.getDeletedAt()!=null)throw new PortalException(404,"Registration not found.");return r;
    }
    static void authorize(Registration r,Authentication actor,boolean read){
        if(role(actor,"ADMIN")||(read&&role(actor,"FINANCE")))return;
        if(!role(actor,"PARTICIPANT")||!r.getOwner().getUsername().equals(actor.getName()))throw new AccessDeniedException("You cannot access this registration.");
    }
    static void editable(Registration r,long expectedVersion){
        if(r.getPaymentStatus()!=PaymentStatus.UNPAID)throw new PortalException(409,"Pending or paid registrations cannot be edited or deleted.");
        if(r.getVersion()!=expectedVersion)throw new PortalException(409,"This registration changed. Reload it before trying again.");
    }
}
