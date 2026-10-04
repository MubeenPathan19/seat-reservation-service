package io.github.mubeenpathan19.seatreservation.repository;

import io.github.mubeenpathan19.seatreservation.api.dto.ReserveRequest;
import io.github.mubeenpathan19.seatreservation.api.dto.ReservationResponse;
import io.github.mubeenpathan19.seatreservation.domain.*;
import io.github.mubeenpathan19.seatreservation.observability.MetricsService;
import io.github.mubeenpathan19.seatreservation.exception.DomainException;
import io.github.mubeenpathan19.seatreservation.service.ReservationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReservationServiceTest {
    private ShowRepository showRepository;
    private SeatRepository seatRepository;
    private ReservationRepository reservationRepository;
    private ReservationSeatRepository reservationSeatRepository;
    private IdempotencyKeyRepository idempotencyRepository;
    private UserShowLockRepository userShowLockRepository;
    private MetricsService metrics;
    private ReservationService service;

    private final UUID showId = UUID.randomUUID();
    private Show show;

    @BeforeEach
    void setUp() {
        showRepository = mock(ShowRepository.class);
        seatRepository = mock(SeatRepository.class);
        reservationRepository = mock(ReservationRepository.class);
        reservationSeatRepository = mock(ReservationSeatRepository.class);
        idempotencyRepository = mock(IdempotencyKeyRepository.class);
        userShowLockRepository = mock(UserShowLockRepository.class);
        metrics = mock(MetricsService.class);
        service = new ReservationService(showRepository, seatRepository, reservationRepository,
                reservationSeatRepository, idempotencyRepository, userShowLockRepository, metrics);
        show = new Show(showId, "test", 25000, 4, Instant.now());
        when(showRepository.findById(showId)).thenReturn(Optional.of(show));
    }

    @Test
    void rejectsDuplicateSeatsBeforeChangingState() {
        Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(() -> service.reserve(showId, "user-1", "key-1",
                new ReserveRequest(List.of("A1", "A1"), null)));
        assertThat(thrown).isInstanceOf(DomainException.class);
        assertThat(thrown).hasMessage("Duplicate seat in request");
        assertThat(((DomainException) thrown).getStatus()).isEqualTo(HttpStatus.CONFLICT);

        verifyNoInteractions(seatRepository, reservationRepository, userShowLockRepository);
    }

    @Test
    void rejectsWhenPerUserLimitWouldBeExceeded() {
        IdempotencyKey key = new IdempotencyKey(UUID.randomUUID(), "user-1", showId,
                "key-1", hash("A1"), Instant.now());
        when(idempotencyRepository.findForUpdate("user-1", showId, "key-1")).thenReturn(Optional.of(key));
        when(userShowLockRepository.findForUpdate(showId, "user-1"))
                .thenReturn(Optional.of(new UserShowLock(new UserShowLockId(showId, "user-1"))));
        when(seatRepository.countConfirmedByUser(showId, "user-1")).thenReturn(4L);

        Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(() -> service.reserve(showId, "user-1", "key-1",
                new ReserveRequest(List.of("A1"), null)));
        assertThat(thrown).isInstanceOf(DomainException.class);
        assertThat(thrown).hasMessage("Per-user seat limit exceeded");
        assertThat(((DomainException) thrown).getStatus()).isEqualTo(HttpStatus.CONFLICT);

        verify(seatRepository, never()).findForUpdate(any(), anyCollection());
        verify(metrics).limit();
    }

    @Test
    void rejectsTakenSeatAsDomainConflict() {
        IdempotencyKey key = new IdempotencyKey(UUID.randomUUID(), "user-1", showId,
                "key-1", hash("A1"), Instant.now());
        when(idempotencyRepository.findForUpdate("user-1", showId, "key-1")).thenReturn(Optional.of(key));
        when(userShowLockRepository.findForUpdate(showId, "user-1"))
                .thenReturn(Optional.of(new UserShowLock(new UserShowLockId(showId, "user-1"))));
        when(seatRepository.countConfirmedByUser(showId, "user-1")).thenReturn(0L);
        Seat seat = new Seat(UUID.randomUUID(), show, "A1", Instant.now());
        seat.confirm(UUID.randomUUID(), "other-user", Instant.now());
        when(seatRepository.findForUpdate(showId, List.of("A1"))).thenReturn(List.of(seat));

        Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(() -> service.reserve(showId, "user-1", "key-1",
                new ReserveRequest(List.of("A1"), null)));
        assertThat(thrown).isInstanceOf(DomainException.class);
        assertThat(thrown).hasMessage("One or more requested seats are already taken");
        assertThat(((DomainException) thrown).getStatus()).isEqualTo(HttpStatus.CONFLICT);

        verify(metrics).seatTaken();
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void successfulReservationCreatesOneReservationForAllSeats() {
        IdempotencyKey key = new IdempotencyKey(UUID.randomUUID(), "user-1", showId,
                "key-1", hash("A1", "A2"), Instant.now());
        when(idempotencyRepository.findForUpdate("user-1", showId, "key-1")).thenReturn(Optional.of(key));
        when(userShowLockRepository.findForUpdate(showId, "user-1"))
                .thenReturn(Optional.of(new UserShowLock(new UserShowLockId(showId, "user-1"))));
        when(seatRepository.countConfirmedByUser(showId, "user-1")).thenReturn(0L);
        Seat a1 = new Seat(UUID.randomUUID(), show, "A1", Instant.now());
        Seat a2 = new Seat(UUID.randomUUID(), show, "A2", Instant.now());
        when(seatRepository.findForUpdate(showId, List.of("A1", "A2"))).thenReturn(List.of(a1, a2));
        when(reservationSeatRepository.findByReservationId(any())).thenReturn(List.of());

        ReservationResponse response = service.reserve(showId, "user-1", "key-1",
                new ReserveRequest(List.of("A2", "A1"), null));

        assertThat(response.userId()).isEqualTo("user-1");
        assertThat(response.showId()).isEqualTo(showId);
        assertThat(response.seats()).containsExactly("A1", "A2");
        assertThat(response.amountPaise()).isEqualTo(50000);
        assertThat(response.status()).isEqualTo("confirmed");
        assertThat(a1.getStatus()).isEqualTo(SeatStatus.CONFIRMED);
        assertThat(a2.getStatus()).isEqualTo(SeatStatus.CONFIRMED);
        assertThat(key.getReservationId()).isNotNull();
        verify(reservationRepository).save(any(Reservation.class));
        verify(idempotencyRepository).save(key);
        verify(metrics).confirmed();
    }

    private static String hash(String... seats) {
        try {
            var md = java.security.MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(String.join("\u001f", Arrays.stream(seats).sorted().toList())
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
