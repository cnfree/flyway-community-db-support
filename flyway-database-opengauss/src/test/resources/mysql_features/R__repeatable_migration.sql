CREATE TABLE IF NOT EXISTS ${flyway:defaultSchema}.repeatable_log(
    run_time TIMESTAMP DEFAULT now(),
    message VARCHAR(255)
);

INSERT INTO ${flyway:defaultSchema}.repeatable_log(message) VALUES ('repeatable migration executed');