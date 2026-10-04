package io.github.mubeenpathan19.seatreservation.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.*;

public record ShowResponse(
        UUID id,
        String name,
        @JsonProperty("price_paise") long pricePaise,
        @JsonProperty("per_user_limit") int perUserLimit,
        @JsonProperty("total_seats") int totalSeats,
        @JsonProperty("available_seats") int availableSeats,
        @JsonProperty("held_seats") int heldSeats,
        @JsonProperty("confirmed_seats") int confirmedSeats,
        List<SeatResponse> seats) {}
