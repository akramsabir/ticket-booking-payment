CREATE TABLE venues (
                        id           BIGINT AUTO_INCREMENT PRIMARY KEY,
                        name         VARCHAR(150)  NOT NULL,
                        address      VARCHAR(255),
                        city         VARCHAR(100)  NOT NULL,
                        state        VARCHAR(100),
                        country      VARCHAR(100)  NOT NULL,
                        pincode      VARCHAR(10),
                        latitude     DECIMAL(9,6),
                        longitude    DECIMAL(9,6),
                        timezone     VARCHAR(50)   NOT NULL DEFAULT 'Asia/Kolkata',
                        status       VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
                        created_by   VARCHAR(100)  NOT NULL DEFAULT 'system',
                        created_date DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                        updated_by   VARCHAR(100)  NOT NULL DEFAULT 'system',
                        updated_date DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);

-- The browse page filters venues by city and status.
CREATE INDEX idx_venues_city_status ON venues (city, status);

-- Safety net: the same venue name cannot exist twice in one city.
CREATE UNIQUE INDEX uq_venues_name_city ON venues (name, city);