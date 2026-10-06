CREATE TABLE IF NOT EXISTS halls (
                       id           BIGINT AUTO_INCREMENT PRIMARY KEY,
                       venue_id     BIGINT       NOT NULL,
                       name         VARCHAR(100) NOT NULL,
                       total_seats  INT          NOT NULL,
                       layout_json  JSON,
                       status       VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
                       created_by   VARCHAR(100) NOT NULL DEFAULT 'system',
                       created_date DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                       updated_by   VARCHAR(100) NOT NULL DEFAULT 'system',
                       updated_date DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                       CONSTRAINT fk_halls_venue FOREIGN KEY (venue_id) REFERENCES venues (id),
                       CONSTRAINT uq_halls_venue_name UNIQUE (venue_id, name),
                       CONSTRAINT chk_halls_total_seats CHECK (total_seats > 0)
);