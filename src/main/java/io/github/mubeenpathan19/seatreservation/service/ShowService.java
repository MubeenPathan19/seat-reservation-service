package io.github.mubeenpathan19.seatreservation.service;

import io.github.mubeenpathan19.seatreservation.api.dto.*;
import io.github.mubeenpathan19.seatreservation.domain.*;
import io.github.mubeenpathan19.seatreservation.exception.NotFoundException;
import io.github.mubeenpathan19.seatreservation.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class ShowService {
    private final ShowRepository showRepository;
    private final SeatRepository seatRepository;
    private final int defaultLimit;

    public ShowService(ShowRepository showRepository, SeatRepository seatRepository,
                       org.springframework.core.env.Environment env) {
        this.showRepository = showRepository;
        this.seatRepository = seatRepository;
        this.defaultLimit = Integer.parseInt(env.getProperty("reservation.default-per-user-limit", "4"));
    }

    @Transactional
    public ShowResponse create(CreateShowRequest request) {
        List<String> seats = request.seats().stream().map(String::trim).toList();
        if (new HashSet<>(seats).size() != seats.size())
            throw new io.github.mubeenpathan19.seatreservation.exception.DomainException(
                    org.springframework.http.HttpStatus.CONFLICT, "DUPLICATE_SEAT", "Seat names must be unique");

        int limit = request.perUserLimit() == null ? defaultLimit : request.perUserLimit();
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        Show show = new Show(id, request.name().trim(), request.pricePaise(), limit, now);
        showRepository.save(show);
        for (String seat : seats) seatRepository.save(new Seat(UUID.randomUUID(), show, seat, now));
        return get(id);
    }

    @Transactional(readOnly = true)
    public ShowResponse get(UUID id) {
        Show show = showRepository.findById(id).orElseThrow(() -> new NotFoundException("SHOW_NOT_FOUND", "Show not found"));
        List<Seat> seats = seatRepository.findByShowIdOrderBySeatNumberAsc(id);
        int available = (int) seats.stream().filter(s -> s.getStatus() == SeatStatus.AVAILABLE).count();
        int confirmed = seats.size() - available;
        return new ShowResponse(id, show.getName(), show.getPricePaise(), show.getPerUserLimit(),
                seats.size(), available, 0, confirmed,
                seats.stream().map(s -> new SeatResponse(s.getSeatNumber(), s.getStatus().name().toLowerCase())).toList());
    }
}
