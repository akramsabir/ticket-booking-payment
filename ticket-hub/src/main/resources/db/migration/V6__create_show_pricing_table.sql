CREATE TABLE IF NOT EXISTS show_pricings (
                                             id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                             show_id BIGINT NOT NULL,
                                             seat_tier VARCHAR(30) NOT NULL,
    price DECIMAL(10, 2) NOT NULL,
    currency VARCHAR(10) NOT NULL DEFAULT 'INR',
    created_date DATETIME(6) NOT NULL,
    updated_date DATETIME(6) NOT NULL,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system',

    CONSTRAINT fk_show_pricing_show
    FOREIGN KEY (show_id) REFERENCES shows (id)
    ON DELETE CASCADE,

    CONSTRAINT uq_show_seat_tier
    UNIQUE (show_id, seat_tier),

    CONSTRAINT chk_show_pricing_price_positive
    CHECK (price > 0.00)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_show_pricing_show_id ON show_pricings (show_id);