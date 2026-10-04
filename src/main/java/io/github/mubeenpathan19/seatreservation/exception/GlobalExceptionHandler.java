package io.github.mubeenpathan19.seatreservation.exception;

import io.github.mubeenpathan19.seatreservation.api.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(DomainException.class)
    ResponseEntity<ErrorResponse> domain(DomainException ex,HttpServletRequest req){
        return ResponseEntity.status(ex.getStatus())
            .body(new ErrorResponse(ex.getCode(),ex.getMessage(),requestId(req),Instant.now()));
    }

    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> validation(Exception ex,HttpServletRequest req){
        return ResponseEntity.badRequest().body(new ErrorResponse("VALIDATION_ERROR","Invalid request",requestId(req),Instant.now()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> unexpected(Exception ex,HttpServletRequest req){
        return ResponseEntity.internalServerError()
            .body(new ErrorResponse("INTERNAL_ERROR","Unexpected server error",requestId(req),Instant.now()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleInvalidJson(
            HttpMessageNotReadableException ex,HttpServletRequest req) {

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(
                        "INVALID_REQUEST",
                        "Request body is invalid",
                        requestId(req),
                        java.time.Instant.now()
                ));
    }

    private String requestId(HttpServletRequest r){
        String id=r.getHeader("X-Request-Id");
        return id==null||id.isBlank()?UUID.randomUUID().toString():id;
    }

}
