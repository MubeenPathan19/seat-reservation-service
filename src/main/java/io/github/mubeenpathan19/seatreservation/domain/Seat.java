package io.github.mubeenpathan19.seatreservation.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "seats", uniqueConstraints = @UniqueConstraint(name = "uq_show_seat", columnNames = {"show_id", "seat_number"}))
public class Seat {
    @Id
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "show_id", nullable = false)
    private Show show;
    @Column(name = "seat_number", nullable = false, length = 50)
    private String seatNumber;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SeatStatus status;
    @Column(name = "reservation_id")
    private UUID reservationId;
    @Column(name = "user_id", length = 200)
    private String userId;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Seat() {}

    public Seat(UUID id, Show show, String seatNumber, Instant now) {
        this.id = id; this.show = show; this.seatNumber = seatNumber;
        this.status = SeatStatus.AVAILABLE; this.createdAt = now; this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public Show getShow() { return show; }
    public String getSeatNumber() { return seatNumber; }
    public SeatStatus getStatus() { return status; }
    public UUID getReservationId() { return reservationId; }
    public String getUserId() { return userId; }

    public void confirm(UUID reservationId, String userId, Instant now) {
        this.status = SeatStatus.CONFIRMED;
        this.reservationId = reservationId;
        this.userId = userId;
        this.updatedAt = now;
    }

    public void release(Instant now) {
        this.status = SeatStatus.AVAILABLE;
        this.reservationId = null;
        this.userId = null;
        this.updatedAt = now;
    }
}
