package io.github.mubeenpathan19.seatreservation.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import java.util.List;

public record ReserveRequest(
        @NotEmpty @Size(max = 20) List<@NotBlank @Size(max = 50) String> seats,
        @JsonProperty("idempotency_key") @Size(max = 200) String idempotencyKey
) {}
