package edu.university.workshop.web;
import jakarta.validation.constraints.*;
public class RegistrationForm {
    @NotBlank @Size(max=100) private String attendeeName;
    @NotBlank @Email @Size(max=254) private String email;
    @NotBlank @Pattern(regexp="\\+91[6-9][0-9]{9}",message="Use +91 followed by a valid 10 digit mobile number") private String mobile;
    @NotBlank @Size(max=150) private String organisation;
    @Min(0) private long version;
    public String getAttendeeName(){return attendeeName;} public void setAttendeeName(String v){attendeeName=v;}
    public String getEmail(){return email;} public void setEmail(String v){email=v;}
    public String getMobile(){return mobile;} public void setMobile(String v){mobile=v;}
    public String getOrganisation(){return organisation;} public void setOrganisation(String v){organisation=v;}
    public long getVersion(){return version;} public void setVersion(long v){version=v;}
}
