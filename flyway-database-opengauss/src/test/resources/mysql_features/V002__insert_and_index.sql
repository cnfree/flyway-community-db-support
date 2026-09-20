CREATE TABLE IF NOT EXISTS ${flyway:defaultSchema}.some_table(
    id INT,
    text VARCHAR(255)
);

INSERT INTO ${flyway:defaultSchema}.some_table(id, text) VALUES (1, 'insert from v2');

CREATE INDEX IF NOT EXISTS idx_some_table_text ON ${flyway:defaultSchema}.some_table(text);

CREATE VIEW ${flyway:defaultSchema}.some_view AS
SELECT * FROM ${flyway:defaultSchema}.some_table WHERE text LIKE '%v2%';