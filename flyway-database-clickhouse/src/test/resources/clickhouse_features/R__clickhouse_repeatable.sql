CREATE TABLE IF NOT EXISTS test.ch_repeatable_log(
    run_time DateTime64(3) DEFAULT now64(3),
    event_type String,
    message String
) ENGINE = MergeTree()
ORDER BY (run_time, event_type);

INSERT INTO test.ch_repeatable_log(event_type, message) VALUES ('migration', 'repeatable migration executed');