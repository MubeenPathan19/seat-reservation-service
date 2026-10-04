package io.github.mubeenpathan19.seatreservation.repository;

import jakarta.persistence.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.*;

@Entity
@Table(name = "user_show_locks")
class UserShowLock {

    @EmbeddedId
    private UserShowLockId id;

    protected UserShowLock() {}

    UserShowLock(UserShowLockId id) {
        this.id = id;
    }
}

@Embeddable
class UserShowLockId implements java.io.Serializable {

    private UUID showId;
    private String userId;

    protected UserShowLockId() {}

    UserShowLockId(UUID showId, String userId) {
        this.showId = showId;
        this.userId = userId;
    }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserShowLockId x)) return false;
        return showId.equals(x.showId) && userId.equals(x.userId);
    }

    public int hashCode() {
        return 31 * showId.hashCode() + userId.hashCode();
    }
}

public interface UserShowLockRepository extends JpaRepository<UserShowLock, UserShowLockId> {

    @Modifying
    @Query(value = """
        INSERT INTO user_show_locks (show_id, user_id)
        VALUES (:showId, :userId)
        ON CONFLICT (show_id, user_id) DO NOTHING
        """, nativeQuery = true)
    int insertIfAbsent(
            @Param("showId") UUID showId,
            @Param("userId") String userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select u
        from UserShowLock u
        where u.id.showId = :showId
          and u.id.userId = :userId
        """)
    Optional<UserShowLock> findForUpdate(
            @Param("showId") UUID showId,
            @Param("userId") String userId);
}