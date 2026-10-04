package io.github.mubeenpathan19.seatreservation.service;

import io.github.mubeenpathan19.seatreservation.api.dto.*;
import io.github.mubeenpathan19.seatreservation.domain.*;
import io.github.mubeenpathan19.seatreservation.exception.*;
import io.github.mubeenpathan19.seatreservation.observability.MetricsService;
import io.github.mubeenpathan19.seatreservation.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

@Service
public class ReservationService {
    private final ShowRepository showRepository;
    private final SeatRepository seatRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationSeatRepository reservationSeatRepository;
    private final IdempotencyKeyRepository idempotencyRepository;
    private final UserShowLockRepository userShowLockRepository;
    private final MetricsService metrics;

    public ReservationService(ShowRepository showRepository,SeatRepository seatRepository,
                              ReservationRepository reservationRepository,ReservationSeatRepository reservationSeatRepository,
                              IdempotencyKeyRepository idempotencyRepository,UserShowLockRepository userShowLockRepository,
                              MetricsService metrics){
        this.showRepository=showRepository;this.seatRepository=seatRepository;
        this.reservationRepository=reservationRepository;this.reservationSeatRepository=reservationSeatRepository;
        this.idempotencyRepository=idempotencyRepository;this.userShowLockRepository=userShowLockRepository;
        this.metrics=metrics;
    }

    @Transactional
    public ReservationResponse reserve(UUID showId,String userId,String idempotencyKey,ReserveRequest request){
        if(idempotencyKey==null||idempotencyKey.isBlank()||idempotencyKey.length()>200)
            throw new DomainException(HttpStatus.BAD_REQUEST,"IDEMPOTENCY_KEY_REQUIRED","Idempotency key is required in Idempotency-Key header or idempotency_key body field");

        Show show=showRepository.findById(showId).orElseThrow(()->new NotFoundException("SHOW_NOT_FOUND","Show not found"));
        if (request == null || request.seats() == null || request.seats().isEmpty())
            throw new DomainException(HttpStatus.BAD_REQUEST, "SEATS_REQUIRED", "At least one seat is required");

        List<String> requested=request.seats().stream().map(String::trim).distinct().sorted().toList();
        if(requested.size()!=request.seats().size())
            throw new DomainException(HttpStatus.CONFLICT,"DUPLICATE_SEAT","Duplicate seat in request");

        String hash=hash(requested);

        // Serialize all concurrent uses of the same idempotency key.
        idempotencyRepository.insertIfAbsent(UUID.randomUUID(),userId,showId,idempotencyKey,hash,Instant.now());
        IdempotencyKey key=idempotencyRepository.findForUpdate(userId,showId,idempotencyKey)
            .orElseThrow(()->new IllegalStateException("Idempotency row missing"));

        if(!key.getRequestHash().equals(hash)){
            metrics.mismatch();
            throw new DomainException(HttpStatus.CONFLICT,"IDEMPOTENCY_KEY_REUSED",
                "Idempotency key was already used with a different request");
        }
        if(key.getReservationId()!=null){
            metrics.replay();
            Reservation existing=reservationRepository.findById(key.getReservationId())
                .orElseThrow(()->new IllegalStateException("Reservation missing for idempotency key"));
            return toResponse(existing);
        }

        // Serialize this user's reservations for this show. This protects the limit
        // even when concurrent requests target different seats.
        userShowLockRepository.insertIfAbsent(showId,userId);
        userShowLockRepository.findForUpdate(showId,userId)
            .orElseThrow(()->new IllegalStateException("User/show lock row missing"));

        long existingCount=seatRepository.countConfirmedByUser(showId,userId);
        if(existingCount+requested.size()>show.getPerUserLimit()){
            metrics.limit();
            throw new DomainException(HttpStatus.CONFLICT,"PER_USER_LIMIT","Per-user seat limit exceeded");
        }

        // Pessimistic locks are acquired in deterministic seat order.
        List<Seat> seats=seatRepository.findForUpdate(showId,requested);
        if(seats.size()!=requested.size()){
            metrics.seatTaken();
            throw new DomainException(HttpStatus.CONFLICT,"SEAT_NOT_FOUND","One or more requested seats do not exist");
        }

        boolean allAvailable=seats.stream().allMatch(s->s.getStatus()==SeatStatus.AVAILABLE);
        if(!allAvailable){
            metrics.seatTaken();
            throw new DomainException(HttpStatus.CONFLICT,"SEAT_TAKEN","One or more requested seats are already taken");
        }

        UUID reservationId=UUID.randomUUID();
        Instant now=Instant.now();
        long amount;
        try {
            amount = Math.multiplyExact(show.getPricePaise(), requested.size());
        } catch (ArithmeticException ex) {
            throw new DomainException(HttpStatus.BAD_REQUEST, "AMOUNT_TOO_LARGE", "Reservation amount is too large");
        }
        Reservation reservation=new Reservation(reservationId,show,userId,amount,now);
        reservationRepository.save(reservation);
        for(Seat seat:seats){
            seat.confirm(reservationId,userId,now);
            reservationSeatRepository.save(new ReservationSeat(reservation,seat));
        }
        key.setReservationId(reservationId);
        idempotencyRepository.save(key);
        metrics.confirmed();
        return toResponse(reservation,requested);
    }

    @Transactional
    public ReservationResponse cancel(UUID reservationId,String userId){
        Reservation reservation=reservationRepository.findForUpdate(reservationId)
            .orElseThrow(()->new NotFoundException("RESERVATION_NOT_FOUND","Reservation not found"));
        if(!reservation.getUserId().equals(userId))
            throw new DomainException(HttpStatus.FORBIDDEN,"NOT_OWNER","Only the reservation owner can cancel it");
        if(reservation.getStatus()==ReservationStatus.CANCELLED)
            return toResponse(reservation);

        // Reservation is locked first, then its seats are locked in deterministic order.
        List<ReservationSeat> links=reservationSeatRepository.findByReservationId(reservationId);
        List<String> names=links.stream().map(ReservationSeat::getSeatNumber).sorted().toList();
        if(!names.isEmpty()){
            List<Seat> seats=seatRepository.findForUpdate(reservation.getShow().getId(),names);
            Instant now=Instant.now();
            for(Seat seat:seats){
                if(reservationId.equals(seat.getReservationId()) && seat.getStatus()==SeatStatus.CONFIRMED)
                    seat.release(now);
            }
        }
        reservation.cancel(Instant.now());
        return toResponse(reservation);
    }

    private String hash(List<String> seats){
        try{
            MessageDigest md=MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(String.join("\u001f",seats).getBytes(StandardCharsets.UTF_8)));
        }catch(Exception e){throw new IllegalStateException(e);}
    }

    private ReservationResponse toResponse(Reservation r){
        List<ReservationSeat> links=reservationSeatRepository.findByReservationId(r.getId());
        return toResponse(r,links.stream().map(ReservationSeat::getSeatNumber).sorted().toList());
    }
    private ReservationResponse toResponse(Reservation r,List<String> seats){
        return new ReservationResponse(r.getId(),r.getShow().getId(),r.getUserId(),seats,r.getAmountPaise(),r.getStatus().name().toLowerCase());
    }
}
