CREATE TABLE IF NOT EXISTS events (
                                      id               BIGINT AUTO_INCREMENT PRIMARY KEY,
                                      title            VARCHAR(200) NOT NULL,
    category         VARCHAR(30)  NOT NULL,
    duration_min     INT          NOT NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    description      TEXT,
    language         VARCHAR(30),
    age_rating       VARCHAR(10),
    release_date     DATE,
    poster_url       VARCHAR(500),

    -- Auditing columns matching BaseEntity
    created_date     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_date     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by       VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_by       VARCHAR(100) NOT NULL DEFAULT 'system',

    CONSTRAINT uk_event_title_category_release UNIQUE (title, category, release_date)
    );

CREATE INDEX idx_events_status_category ON events (status, category);