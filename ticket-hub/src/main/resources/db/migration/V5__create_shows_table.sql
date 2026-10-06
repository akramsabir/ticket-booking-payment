
CREATE TABLE IF NOT EXISTS shows (
                                     id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                     event_id BIGINT NOT NULL,
                                     hall_id BIGINT NOT NULL,
                                     start_time DATETIME(6) NOT NULL,
    end_time DATETIME(6) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'SCHEDULED',
    created_date DATETIME(6) NOT NULL,
    updated_date DATETIME(6) NOT NULL,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system',

    CONSTRAINT fk_shows_event
    FOREIGN KEY (event_id) REFERENCES events (id)
    ON DELETE RESTRICT,

    CONSTRAINT fk_shows_hall
    FOREIGN KEY (hall_id) REFERENCES halls (id)
    ON DELETE RESTRICT,

    CONSTRAINT chk_shows_time_order
    CHECK (end_time > start_time)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Indexes matching your JPA Show entity for fast querying and overlap checking
CREATE INDEX idx_shows_hall_times ON shows (hall_id, start_time, end_time);
CREATE INDEX idx_shows_event_status ON shows (event_id, status);
CREATE INDEX idx_shows_start_time ON shows (start_time);