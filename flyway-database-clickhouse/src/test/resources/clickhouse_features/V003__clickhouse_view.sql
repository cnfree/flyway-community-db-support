CREATE VIEW ${flyway:defaultSchema}.ch_active_view AS
SELECT id, str_val, category
FROM ${flyway:defaultSchema}.ch_datatypes
WHERE status = 'active';