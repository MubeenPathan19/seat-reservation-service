package io.github.mubeenpathan19.seatreservation.observability;

import io.github.mubeenpathan19.seatreservation.domain.SeatStatus;
import io.github.mubeenpathan19.seatreservation.repository.SeatRepository;
import io.micrometer.core.instrument.*;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

@Service
public class MetricsService {
    private final MeterRegistry registry;
    private final SeatRepository seatRepository;
    private Counter confirmed;
    private Counter declinedSeatTaken;
    private Counter declinedLimit;
    private Counter declinedReplay;
    private Counter declinedIdempotencyMismatch;

    public MetricsService(MeterRegistry registry,SeatRepository seatRepository){
        this.registry=registry;this.seatRepository=seatRepository;
    }

    @PostConstruct
    void init(){
        confirmed=Counter.builder("reservations_confirmed_total").description("Confirmed reservations").register(registry);
        declinedSeatTaken=Counter.builder("reservations_declined_total").tag("reason","seat-taken").register(registry);
        declinedLimit=Counter.builder("reservations_declined_total").tag("reason","per-user-limit").register(registry);
        declinedReplay=Counter.builder("reservations_declined_total").tag("reason","idempotent-replay").register(registry);
        declinedIdempotencyMismatch=Counter.builder("reservations_declined_total").tag("reason","idempotency-mismatch").register(registry);

        Gauge.builder("seats_available",seatRepository, r->r.countByStatus(SeatStatus.AVAILABLE))
            .description("Currently available seats across all shows").register(registry);
        Gauge.builder("seats_confirmed",seatRepository, r->r.countByStatus(SeatStatus.CONFIRMED))
            .description("Currently confirmed seats across all shows").register(registry);
    }

    public void confirmed(){confirmed.increment();}
    public void seatTaken(){declinedSeatTaken.increment();}
    public void limit(){declinedLimit.increment();}
    public void replay(){declinedReplay.increment();}
    public void mismatch(){declinedIdempotencyMismatch.increment();}
}
