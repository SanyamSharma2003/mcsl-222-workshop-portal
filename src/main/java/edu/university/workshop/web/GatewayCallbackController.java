package edu.university.workshop.web;
import edu.university.workshop.service.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.io.IOException;
import java.util.Map;

@RestController
public class GatewayCallbackController {
    private final PaymentService payments;
    public GatewayCallbackController(PaymentService payments){this.payments=payments;}
    @PostMapping(path="/gateway/callback",consumes="application/json",produces="application/json")
    public Map<String,String> callback(HttpServletRequest request,@RequestHeader(value="X-Payment-Signature",required=false) String signature) throws IOException {
        byte[] raw=request.getInputStream().readNBytes(8193);
        payments.webhook(raw,signature);return Map.of("status","accepted");
    }
    @ExceptionHandler(PortalException.class)
    ResponseEntity<Map<String,String>> invalid(PortalException e){return ResponseEntity.status(e.getStatus()).body(Map.of("error",e.getMessage()));}
}
