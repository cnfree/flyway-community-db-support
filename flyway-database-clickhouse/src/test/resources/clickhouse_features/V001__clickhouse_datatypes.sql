CREATE TABLE ${flyway:defaultSchema}.ch_datatypes(
    id Int32,
    tiny_val Int8 DEFAULT 1,
    small_val Int16,
    big_val Int64,
    unsigned_val UInt32,
    float_val Float64,
    str_val String,
    fixed_str FixedString(16),
    price Decimal(15, 4),
    nullable_str Nullable(String),
    tag_array Array(Int32),
    status Enum8('active' = 1, 'inactive' = 2, 'pending' = 3),
    category LowCardinality(String),
    is_deleted Bool DEFAULT false,
    created_date Date,
    created_at DateTime64(3) DEFAULT now64(3)
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(created_date)
ORDER BY (id, created_date)
SETTINGS index_granularity = 8192;