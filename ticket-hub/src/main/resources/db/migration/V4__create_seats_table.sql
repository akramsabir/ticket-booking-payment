CREATE TABLE IF NOT EXISTS seats (
                                     id           BIGINT AUTO_INCREMENT PRIMARY KEY,
                                     hall_id      BIGINT       NOT NULL,
                                     row_label    VARCHAR(10)  NOT NULL,
    seat_number  INT          NOT NULL,
    seat_code    VARCHAR(20)  NOT NULL,
    tier         VARCHAR(30)  NOT NULL DEFAULT 'CLASSIC',
    status       VARCHAR(20)  NOT NULL DEFAULT 'AVAILABLE',

    -- Auditing columns matching BaseEntity
    created_by   VARCHAR(100) NOT NULL DEFAULT 'system',
    created_date DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_by   VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_date DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT fk_seats_hall FOREIGN KEY (hall_id) REFERENCES halls (id) ON DELETE CASCADE,
    CONSTRAINT uq_seats_hall_row_number UNIQUE (hall_id, row_label, seat_number),
    CONSTRAINT uq_seats_hall_code UNIQUE (hall_id, seat_code),
    CONSTRAINT chk_seats_number CHECK (seat_number > 0)
    );

-- Index for querying seats by hall and tier
CREATE INDEX idx_seats_hall_tier ON seats (hall_id, tier);
CREATE INDEX idx_seats_hall_status ON seats (hall_id, status);