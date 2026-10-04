package io.github.mubeenpathan19.seatreservation.api;

import io.github.mubeenpathan19.seatreservation.api.dto.*;
import io.github.mubeenpathan19.seatreservation.service.ReservationService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
public class ReservationController {
    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping("/shows/{showId}/reserve")
    public ResponseEntity<ReservationResponse> reserve(
            @PathVariable UUID showId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyHeader,
            @Valid @RequestBody ReserveRequest request,
            Authentication authentication) {
        String idempotencyKey = idempotencyKeyHeader != null && !idempotencyKeyHeader.isBlank()
                ? idempotencyKeyHeader
                : request.idempotencyKey();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reservationService.reserve(showId, authentication.getName(), idempotencyKey, request));
    }

    @PostMapping("/reservations/{reservationId}/cancel")
    public ReservationResponse cancel(@PathVariable UUID reservationId, Authentication authentication) {
        return reservationService.cancel(reservationId, authentication.getName());
    }
}
