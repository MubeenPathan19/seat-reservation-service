package io.github.mubeenpathan19.seatreservation.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import java.util.List;

public record CreateShowRequest(
        @NotBlank @Size(max = 200) String name,
        @NotEmpty @Size(max = 10000) List<@NotBlank @Size(max = 50) String> seats,
        @JsonProperty("price_paise") @NotNull @PositiveOrZero Long pricePaise,
        @JsonProperty("per_user_limit") @Positive Integer perUserLimit
) {}
