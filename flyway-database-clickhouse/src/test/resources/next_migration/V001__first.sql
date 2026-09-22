CREATE TABLE ${flyway:defaultSchema}.some_table(
    id Int32,
    text String
) ENGINE = MergeTree()
ORDER BY id;

INSERT INTO ${flyway:defaultSchema}.some_table(id, text) VALUES (1, 'first');
INSERT INTO ${flyway:defaultSchema}.some_table(id, text) VALUES (2, 'second');