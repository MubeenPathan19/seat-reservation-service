package io.github.mubeenpathan19.seatreservation.repository;
import io.github.mubeenpathan19.seatreservation.domain.Show;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface ShowRepository extends JpaRepository<Show, UUID> {}
