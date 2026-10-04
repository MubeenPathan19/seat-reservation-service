package io.github.mubeenpathan19.seatreservation.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mubeenpathan19.seatreservation.api.dto.CreateShowRequest;
import io.github.mubeenpathan19.seatreservation.api.dto.ReserveRequest;
import io.github.mubeenpathan19.seatreservation.api.dto.ReservationResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ApiContractTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void createShowUsesAssignmentFieldNames() throws Exception {
        CreateShowRequest request = mapper.readValue("""
                {"name":"friday-night","seats":["A1"],"price_paise":25000,"per_user_limit":4}
                """, CreateShowRequest.class);

        assertThat(request.pricePaise()).isEqualTo(25000L);
        assertThat(request.perUserLimit()).isEqualTo(4);
        assertThat(mapper.writeValueAsString(request))
                .contains("\"price_paise\":25000")
                .contains("\"per_user_limit\":4");
    }

    @Test
    void reserveAcceptsIdempotencyKeyInBody() throws Exception {
        ReserveRequest request = mapper.readValue(
                "{\"seats\":[\"A12\"],\"idempotency_key\":\"booking-001\"}",
                ReserveRequest.class);

        assertThat(request.idempotencyKey()).isEqualTo("booking-001");
    }

    @Test
    void reservationResponseUsesSnakeCaseContract() throws Exception {
        UUID reservationId = UUID.randomUUID();
        UUID showId = UUID.randomUUID();
        ReservationResponse response = new ReservationResponse(
                reservationId, showId, "user-1", List.of("A12"), 25000, "confirmed");

        String json = mapper.writeValueAsString(response);
        assertThat(json)
                .contains("\"reservation_id\":\"" + reservationId + "\"")
                .contains("\"show_id\":\"" + showId + "\"")
                .contains("\"user_id\":\"user-1\"")
                .contains("\"amount_paise\":25000");
    }
}
