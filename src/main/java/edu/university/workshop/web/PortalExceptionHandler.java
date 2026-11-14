package edu.university.workshop.web;

import edu.university.workshop.service.PortalException;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.Model;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@ControllerAdvice
public class PortalExceptionHandler {
    @ExceptionHandler(PortalException.class)
    public String domainError(PortalException error, HttpServletResponse response, Model model) {
        return error(error.getStatus(), error.getMessage(), response, model);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public String forbidden(AccessDeniedException error, HttpServletResponse response, Model model) {
        return error(403, "Your account is not allowed to perform this action.", response, model);
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class, ConstraintViolationException.class})
    public String invalidRequest(Exception error, HttpServletResponse response, Model model) {
        return error(400, "The request contains missing or invalid values. Please check the form and try again.", response, model);
    }

    @ExceptionHandler({ConcurrencyFailureException.class, DataIntegrityViolationException.class})
    public String conflict(Exception error, HttpServletResponse response, Model model) {
        // Do not render SQL messages, constraint names or internal stack traces.
        return error(409, "The request conflicts with another update or an existing payment reference.", response, model);
    }

    private String error(int status, String message, HttpServletResponse response, Model model) {
        response.setStatus(status);
        model.addAttribute("status", status);
        model.addAttribute("message", message);
        return "portal-error";
    }
}
