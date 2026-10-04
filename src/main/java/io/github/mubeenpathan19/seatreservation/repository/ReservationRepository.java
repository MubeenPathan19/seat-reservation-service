package io.github.mubeenpathan19.seatreservation.repository;
import io.github.mubeenpathan19.seatreservation.domain.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reservation r where r.id=:id")
    Optional<Reservation> findForUpdate(@Param("id") UUID id);
}
