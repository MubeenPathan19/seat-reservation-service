CREATE TABLE shows (
    id UUID PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    price_paise BIGINT NOT NULL CHECK (price_paise >= 0),
    per_user_limit INTEGER NOT NULL CHECK (per_user_limit > 0),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE seats (
    id UUID PRIMARY KEY,
    show_id UUID NOT NULL REFERENCES shows(id),
    seat_number VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('AVAILABLE', 'CONFIRMED')),
    reservation_id UUID NULL,
    user_id VARCHAR(200) NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_show_seat UNIQUE(show_id, seat_number)
);

CREATE INDEX idx_seats_show_status ON seats(show_id, status);
CREATE INDEX idx_seats_show_user ON seats(show_id, user_id);

CREATE TABLE reservations (
    id UUID PRIMARY KEY,
    show_id UUID NOT NULL REFERENCES shows(id),
    user_id VARCHAR(200) NOT NULL,
    amount_paise BIGINT NOT NULL CHECK (amount_paise >= 0),
    status VARCHAR(20) NOT NULL CHECK (status IN ('CONFIRMED', 'CANCELLED')),
    created_at TIMESTAMPTZ NOT NULL,
    cancelled_at TIMESTAMPTZ NULL
);

CREATE INDEX idx_reservations_show_user_status ON reservations(show_id, user_id, status);

CREATE TABLE reservation_seats (
    reservation_id UUID NOT NULL REFERENCES reservations(id),
    seat_id UUID NOT NULL REFERENCES seats(id),
    seat_number VARCHAR(50) NOT NULL,
    PRIMARY KEY (reservation_id, seat_id)
);

CREATE TABLE idempotency_keys (
    id UUID PRIMARY KEY,
    user_id VARCHAR(200) NOT NULL,
    show_id UUID NOT NULL REFERENCES shows(id),
    idempotency_key VARCHAR(200) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    reservation_id UUID NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_idempotency UNIQUE(user_id, show_id, idempotency_key)
);

CREATE INDEX idx_idempotency_reservation ON idempotency_keys(reservation_id);

CREATE TABLE user_show_locks (
    show_id UUID NOT NULL REFERENCES shows(id),
    user_id VARCHAR(200) NOT NULL,
    PRIMARY KEY(show_id, user_id)
);
