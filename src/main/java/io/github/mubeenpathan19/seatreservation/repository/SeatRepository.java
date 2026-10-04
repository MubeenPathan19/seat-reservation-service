package io.github.mubeenpathan19.seatreservation.repository;

import io.github.mubeenpathan19.seatreservation.domain.Seat;
import io.github.mubeenpathan19.seatreservation.domain.SeatStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface SeatRepository extends JpaRepository<Seat, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Seat s where s.show.id = :showId and s.seatNumber in :seatNumbers order by s.seatNumber asc")
    List<Seat> findForUpdate(@Param("showId") UUID showId, @Param("seatNumbers") Collection<String> seatNumbers);

    List<Seat> findByShowIdOrderBySeatNumberAsc(UUID showId);

    long countByStatus(SeatStatus status);
    long countByShowIdAndStatus(UUID showId, SeatStatus status);

    @Query("select count(s) from Seat s where s.show.id=:showId and s.userId=:userId and s.status='CONFIRMED'")
    long countConfirmedByUser(@Param("showId") UUID showId, @Param("userId") String userId);
}
