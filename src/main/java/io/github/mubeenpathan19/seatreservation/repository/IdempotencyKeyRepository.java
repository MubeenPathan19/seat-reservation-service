package io.github.mubeenpathan19.seatreservation.repository;

import io.github.mubeenpathan19.seatreservation.domain.IdempotencyKey;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, UUID> {
    @Modifying
    @Query(value = """
    INSERT INTO idempotency_keys (id, user_id, show_id, idempotency_key, request_hash, created_at)
    VALUES (:id, :userId, :showId, :key, :hash, :createdAt)
    ON CONFLICT (user_id, show_id, idempotency_key) DO NOTHING
    """, nativeQuery = true)
    int insertIfAbsent(
            @Param("id") UUID id,
            @Param("userId") String userId,
            @Param("showId") UUID showId,
            @Param("key") String key,
            @Param("hash") String hash,
            @Param("createdAt") java.time.Instant createdAt);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from IdempotencyKey i where i.userId=:userId and i.showId=:showId and i.idempotencyKey=:key")
    Optional<IdempotencyKey> findForUpdate(@Param("userId") String userId, @Param("showId") UUID showId, @Param("key") String key);
}
