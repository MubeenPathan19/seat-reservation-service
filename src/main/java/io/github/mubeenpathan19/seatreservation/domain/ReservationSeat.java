package io.github.mubeenpathan19.seatreservation.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;


@Getter
@Setter
@Entity
@Table(name = "reservation_seats")
public class ReservationSeat {
    @EmbeddedId
    private ReservationSeatId id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("reservationId")
    @JoinColumn(name = "reservation_id")
    private Reservation reservation;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("seatId")
    @JoinColumn(name = "seat_id")
    private Seat seat;
    @Column(name = "seat_number", nullable = false, length = 50)
    private String seatNumber;

    protected ReservationSeat() {}

    public ReservationSeat(Reservation reservation, Seat seat) {
        this.id = new ReservationSeatId(reservation.getId(), seat.getId());
        this.reservation=reservation; this.seat=seat; this.seatNumber=seat.getSeatNumber();
    }
}
