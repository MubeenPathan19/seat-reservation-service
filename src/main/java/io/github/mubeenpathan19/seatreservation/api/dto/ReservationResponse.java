package io.github.mubeenpathan19.seatreservation.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.*;

public record ReservationResponse(
        @JsonProperty("reservation_id") UUID reservationId,
        @JsonProperty("show_id") UUID showId,
        @JsonProperty("user_id") String userId,
        List<String> seats,
        @JsonProperty("amount_paise") long amountPaise,
        String status) {}
