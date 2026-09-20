INSERT INTO ${flyway:defaultSchema}.some_table(id, text) VALUES (2, 'second row from v3');

INSERT INTO ${flyway:defaultSchema}.some_table(id, text) VALUES (3, 'third row from v3');

UPDATE ${flyway:defaultSchema}.some_table SET text = 'updated first' WHERE id = 1;