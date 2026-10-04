package io.github.mubeenpathan19.seatreservation.repository;
import io.github.mubeenpathan19.seatreservation.domain.ReservationSeat;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface ReservationSeatRepository extends JpaRepository<ReservationSeat, Object> {
    List<ReservationSeat> findByReservationId(UUID reservationId);
}
