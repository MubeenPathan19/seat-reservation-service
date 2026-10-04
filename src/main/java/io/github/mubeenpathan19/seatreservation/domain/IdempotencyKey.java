package io.github.mubeenpathan19.seatreservation.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "idempotency_keys",
       uniqueConstraints = @UniqueConstraint(name = "uq_idempotency", columnNames = {"user_id","show_id","idempotency_key"}))
public class IdempotencyKey {
    @Id
    private UUID id;
    @Column(name="user_id", nullable=false, length=200)
    private String userId;
    @Column(name="show_id", nullable=false)
    private UUID showId;
    @Column(name="idempotency_key", nullable=false, length=200)
    private String idempotencyKey;
    @Column(name="request_hash", nullable=false, length=64)
    private String requestHash;
    @Column(name="reservation_id")
    private UUID reservationId;
    @Column(name="created_at", nullable=false)
    private Instant createdAt;

    protected IdempotencyKey() {}

    public IdempotencyKey(UUID id, String userId, UUID showId, String key, String requestHash, Instant createdAt) {
        this.id=id; this.userId=userId; this.showId=showId; this.idempotencyKey=key;
        this.requestHash=requestHash; this.createdAt=createdAt;
    }
    public UUID getId(){return id;}
    public String getRequestHash(){return requestHash;}
    public UUID getReservationId(){return reservationId;}
    public void setReservationId(UUID reservationId){this.reservationId=reservationId;}
}
