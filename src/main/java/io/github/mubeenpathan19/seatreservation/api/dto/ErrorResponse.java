package io.github.mubeenpathan19.seatreservation.api.dto;
import java.time.Instant;
public record ErrorResponse(String error, String message, String requestId, Instant timestamp) {}
