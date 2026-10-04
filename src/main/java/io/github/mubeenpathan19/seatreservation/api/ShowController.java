package io.github.mubeenpathan19.seatreservation.api;

import io.github.mubeenpathan19.seatreservation.api.dto.*;
import io.github.mubeenpathan19.seatreservation.service.ShowService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/shows")
public class ShowController {
    private final ShowService showService;

    public ShowController(ShowService showService) {
        this.showService = showService;
    }

    @PostMapping
    public ResponseEntity<ShowResponse> create(
            @Valid @RequestBody CreateShowRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(showService.create(request));
    }

    @GetMapping("/{showId}")
    public ShowResponse get(@PathVariable UUID showId) {
        return showService.get(showId);
    }
}
