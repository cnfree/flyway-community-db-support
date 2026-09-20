CREATE VIEW ${flyway:defaultSchema}.some_view AS
SELECT * FROM ${flyway:defaultSchema}.some_table WHERE text != '';