package io.github.mubeenpathan19.seatreservation.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reservations")
public class Reservation {
    @Id
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "show_id", nullable = false)
    private Show show;
    @Column(name = "user_id", nullable = false, length = 200)
    private String userId;
    @Column(name = "amount_paise", nullable = false)
    private long amountPaise;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationStatus status;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    protected Reservation() {}

    public Reservation(UUID id, Show show, String userId, long amountPaise, Instant createdAt) {
        this.id=id; this.show=show; this.userId=userId; this.amountPaise=amountPaise;
        this.status=ReservationStatus.CONFIRMED; this.createdAt=createdAt;
    }

    public UUID getId(){return id;}
    public Show getShow(){return show;}
    public String getUserId(){return userId;}
    public long getAmountPaise(){return amountPaise;}
    public ReservationStatus getStatus(){return status;}
    public Instant getCreatedAt(){return createdAt;}

    public void cancel(Instant now) {
        this.status = ReservationStatus.CANCELLED;
        this.cancelledAt = now;
    }
}
