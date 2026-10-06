CREATE TABLE IF NOT EXISTS show_seats (
                                          id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                          show_id BIGINT NOT NULL,
                                          seat_id BIGINT NOT NULL,
                                          price DECIMAL(10, 2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    created_date DATETIME(6) NOT NULL,
    updated_date DATETIME(6) NOT NULL,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system',

    CONSTRAINT fk_show_seats_show
    FOREIGN KEY (show_id) REFERENCES shows (id)
    ON DELETE CASCADE,

    CONSTRAINT fk_show_seats_seat
    FOREIGN KEY (seat_id) REFERENCES seats (id)
    ON DELETE CASCADE,

    CONSTRAINT uq_show_seats_show_seat
    UNIQUE (show_id, seat_id),

    CONSTRAINT chk_show_seats_status
    CHECK (status IN ('AVAILABLE', 'RESERVED', 'BOOKED'))
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_show_seats_show_id ON show_seats (show_id);
CREATE INDEX idx_show_seats_status ON show_seats (status);