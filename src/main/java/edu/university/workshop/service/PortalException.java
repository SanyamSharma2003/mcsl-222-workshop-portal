package edu.university.workshop.service;
public class PortalException extends RuntimeException {
    private final int status;
    public PortalException(int status,String message){super(message);this.status=status;}
    public int getStatus(){return status;}
}
