package io.github.mubeenpathan19.seatreservation.domain;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.UUID;

@Embeddable
public class ReservationSeatId implements Serializable {
    private UUID reservationId;
    private UUID seatId;

    protected ReservationSeatId() {}
    public ReservationSeatId(UUID reservationId, UUID seatId) {
        this.reservationId=reservationId; this.seatId=seatId;
    }
    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ReservationSeatId other)) return false;
        return reservationId.equals(other.reservationId) && seatId.equals(other.seatId);
    }
    @Override public int hashCode() { return 31 * reservationId.hashCode() + seatId.hashCode(); }
}
