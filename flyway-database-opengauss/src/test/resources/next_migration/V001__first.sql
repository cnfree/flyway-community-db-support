CREATE TABLE ${flyway:defaultSchema}.some_table(
    id INT,
    text VARCHAR(255)
);

CREATE INDEX some_table_text ON ${flyway:defaultSchema}.some_table(text);

INSERT INTO ${flyway:defaultSchema}.some_table(id, text) VALUES (1, 'first');
INSERT INTO ${flyway:defaultSchema}.some_table(id, text) VALUES (2, 'second');