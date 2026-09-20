CREATE TABLE IF NOT EXISTS ${flyway:defaultSchema}.mysql_features(
    id BIGINT NOT NULL,
    name VARCHAR(100),
    description TEXT,
    price DECIMAL(10,2),
    is_active TINYINT DEFAULT 1,
    created_at DATETIME DEFAULT now(),
    score DOUBLE PRECISION DEFAULT 0.0,
    PRIMARY KEY (id)
);