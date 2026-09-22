/*-
 * ========================LICENSE_START=================================
 * flyway-database-clickhouse
 * ========================================================================
 * Copyright (C) 2010 - 2025 Red Gate Software Ltd
 * ========================================================================
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =========================LICENSE_END==================================
 */

package org.flywaydb.community.database.clickhouse;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.flywaydb.core.api.MigrationState;
import org.flywaydb.core.api.configuration.ClassicConfiguration;
import org.flywaydb.core.internal.database.base.Table;
import org.flywaydb.core.internal.jdbc.JdbcConnectionFactory;
import org.flywaydb.core.internal.jdbc.JdbcTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import javax.sql.DataSource;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@TestMethodOrder(MethodOrderer.MethodName.class)
class ClickHouseSupportTest {

    private static final String URL = "jdbc:clickhouse://192.168.70.100:8123/test";
    private static final String USER = "default";
    private static final String PASSWORD = "DM@2024";
    private static final String DEFAULT_SCHEMA = "test";
    private static final String NEXT_MIGRATION_LOCATION = "next_migration";
    private static final String INITIAL_MIGRATION_LOCATION = "initial_migration";
    private static final String CLICKHOUSE_FEATURES = "clickhouse_features";

    private static final JdbcTemplate jdbcTemplate;

    static {
        try {
            Class.forName("com.clickhouse.jdbc.ClickHouseDriver");
            jdbcTemplate = new JdbcTemplate(
                    DriverManager.getConnection(URL, USER, PASSWORD),
                    new ClickHouseDatabaseType()
            );
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @BeforeEach
    void setUp() {
        try {
            cleanup();
        } catch (Exception e) {
            // Ignore cleanup failures before test
        }
    }

    @AfterEach
    void cleanup() throws SQLException {
        jdbcTemplate.execute("DROP TABLE IF EXISTS " + DEFAULT_SCHEMA + ".some_table");
        jdbcTemplate.execute("DROP VIEW IF EXISTS " + DEFAULT_SCHEMA + ".some_view");
        jdbcTemplate.execute("DROP TABLE IF EXISTS " + DEFAULT_SCHEMA + ".flyway_schema_history");
        jdbcTemplate.execute("DROP TABLE IF EXISTS " + DEFAULT_SCHEMA + ".schema_test_table");
        jdbcTemplate.execute("DROP TABLE IF EXISTS " + DEFAULT_SCHEMA + ".schema_test_table2");
        jdbcTemplate.execute("DROP TABLE IF EXISTS " + DEFAULT_SCHEMA + ".table_test_tbl");
        jdbcTemplate.execute("DROP TABLE IF EXISTS " + DEFAULT_SCHEMA + ".clean_test_table");
        jdbcTemplate.execute("DROP VIEW IF EXISTS " + DEFAULT_SCHEMA + ".clean_test_view");
        jdbcTemplate.execute("DROP TABLE IF EXISTS " + DEFAULT_SCHEMA + ".ch_datatypes");
        jdbcTemplate.execute("DROP VIEW IF EXISTS " + DEFAULT_SCHEMA + ".ch_active_view");
        jdbcTemplate.execute("DROP TABLE IF EXISTS " + DEFAULT_SCHEMA + ".ch_repeatable_log");
        jdbcTemplate.execute("DROP DATABASE IF EXISTS test_schema_ch");
    }

    @Test
    @DisplayName("Flyway migrate creates tables and applies migrations")
    void migrates() throws SQLException {
        Flyway flyway = Flyway.configure()
                .dataSource(URL, USER, PASSWORD)
                .locations(INITIAL_MIGRATION_LOCATION)
                .defaultSchema(DEFAULT_SCHEMA)
                .load();

        assertThat(getAllTablesNames()).isEmpty();

        flyway.migrate();

        assertThat(getAllTablesNames())
                .contains("flyway_schema_history", "some_table");
        assertThat(getFlywayHistoryMigrationDescriptions())
                .isEqualTo(Arrays.asList("first", "second"));
        assertThat(getAllViewsNames())
                .contains("some_view");
    }

    @Test
    @DisplayName("Flyway does not apply a migration several times")
    void doesNotApplyMigrationSeveralTimes() throws SQLException {
        Flyway flyway = Flyway.configure()
                .dataSource(URL, USER, PASSWORD)
                .locations(INITIAL_MIGRATION_LOCATION)
                .defaultSchema(DEFAULT_SCHEMA)
                .load();

        flyway.migrate();
        flyway.migrate();
        flyway.migrate();

        assertThat(countSomeTableRows()).isEqualTo(2);
        assertThat(getFlywayHistoryMigrationDescriptions())
                .isEqualTo(Arrays.asList("first", "second"));
    }

    @Test
    @DisplayName("Flyway applies only new migrations when some were already applied")
    void appliesOnlyNewMigrationsWhenSomeWereAlreadyApplied() throws SQLException {
        Flyway flyway = Flyway.configure()
                .dataSource(URL, USER, PASSWORD)
                .locations(INITIAL_MIGRATION_LOCATION)
                .defaultSchema(DEFAULT_SCHEMA)
                .load();

        flyway.migrate();
        assertThat(countSomeTableRows()).isEqualTo(2);
        assertThat(getFlywayHistoryMigrationDescriptions())
                .isEqualTo(Arrays.asList("first", "second"));

        Flyway.configure()
                .dataSource(URL, USER, PASSWORD)
                .locations(NEXT_MIGRATION_LOCATION)
                .defaultSchema(DEFAULT_SCHEMA)
                .load()
                .migrate();

        assertThat(countSomeTableRows()).isEqualTo(3);
        assertThat(getFlywayHistoryMigrationDescriptions())
                .isEqualTo(Arrays.asList("first", "second", "add more rows"));
    }

    @Test
    @DisplayName("Flyway sets baseline correctly")
    void setsBaseline() throws SQLException {
        Flyway flyway = Flyway.configure()
                .dataSource(URL, USER, PASSWORD)
                .locations(INITIAL_MIGRATION_LOCATION)
                .defaultSchema(DEFAULT_SCHEMA)
                .baselineVersion("123")
                .load();

        assertThat(getAllTablesNames()).isEmpty();

        flyway.baseline();

        assertThat(getAllTablesNames())
                .contains("flyway_schema_history");
        assertThat(getFlywayHistoryMigrationDescriptions())
                .isEqualTo(Arrays.asList("<< Flyway Baseline >>"));
    }

    // -------------------------------------------------------------------------
    // ClickHouse feature tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("ClickHouse Features: CREATE TABLE with various data types and MergeTree engine options")
    void createsTableWithClickHouseDataTypes() throws SQLException {
        Flyway flyway = Flyway.configure()
                .dataSource(URL, USER, PASSWORD)
                .locations(CLICKHOUSE_FEATURES)
                .defaultSchema(DEFAULT_SCHEMA)
                .load();

        flyway.migrate();

        assertThat(getAllTablesNames())
                .contains("ch_datatypes");

        int rowCount = jdbcTemplate.queryForInt(
                "SELECT COUNT() FROM system.columns WHERE database = ? AND \"table\" = 'ch_datatypes'",
                DEFAULT_SCHEMA);
        assertThat(rowCount).isEqualTo(17);
    }

    @Test
    @DisplayName("ClickHouse Features: INSERT data via jdbcTemplate and ALTER TABLE ADD COLUMN via Flyway")
    void clickHouseMutationsAndInsertSelect() throws SQLException {
        Flyway flyway = Flyway.configure()
                .dataSource(URL, USER, PASSWORD)
                .locations(CLICKHOUSE_FEATURES)
                .defaultSchema(DEFAULT_SCHEMA)
                .load();

        flyway.migrate();

        jdbcTemplate.execute("INSERT INTO " + DEFAULT_SCHEMA + ".ch_datatypes(id, small_val, str_val, status, category, created_date) VALUES (1, 100, 'hello', 'active', 'type_a', toDate('2026-01-15'))");
        jdbcTemplate.execute("INSERT INTO " + DEFAULT_SCHEMA + ".ch_datatypes(id, small_val, str_val, status, category, created_date) VALUES (2, 200, 'world', 'inactive', 'type_b', toDate('2026-02-20'))");
        jdbcTemplate.execute("INSERT INTO " + DEFAULT_SCHEMA + ".ch_datatypes(id, small_val, str_val, status, category, created_date) VALUES (3, 300, 'clickhouse', 'pending', 'type_a', toDate('2026-03-10'))");

        int count = jdbcTemplate.queryForInt(
                "SELECT COUNT() FROM " + DEFAULT_SCHEMA + ".ch_datatypes");
        assertThat(count).isEqualTo(3);

        int columnCount = jdbcTemplate.queryForInt(
                "SELECT COUNT() FROM system.columns WHERE database = ? AND \"table\" = 'ch_datatypes'",
                DEFAULT_SCHEMA);
        assertThat(columnCount).isEqualTo(17);
    }

    @Test
    @DisplayName("ClickHouse Features: CREATE VIEW with data inserted via jdbcTemplate")
    void clickHouseViewCreation() throws SQLException {
        Flyway flyway = Flyway.configure()
                .dataSource(URL, USER, PASSWORD)
                .locations(CLICKHOUSE_FEATURES)
                .defaultSchema(DEFAULT_SCHEMA)
                .load();

        flyway.migrate();

        jdbcTemplate.execute("INSERT INTO " + DEFAULT_SCHEMA + ".ch_datatypes(id, small_val, str_val, status, category, created_date) VALUES (1, 100, 'hello', 'active', 'type_a', toDate('2026-01-15'))");
        jdbcTemplate.execute("INSERT INTO " + DEFAULT_SCHEMA + ".ch_datatypes(id, small_val, str_val, status, category, created_date) VALUES (2, 200, 'world', 'inactive', 'type_b', toDate('2026-02-20'))");

        assertThat(getAllViewsNames()).contains("ch_active_view");

        int viewRows = jdbcTemplate.queryForInt(
                "SELECT COUNT() FROM " + DEFAULT_SCHEMA + ".ch_active_view");
        assertThat(viewRows).isEqualTo(1);
    }

    @Test
    @DisplayName("ClickHouse Features: ${flyway:defaultSchema} placeholder replacement works")
    void placeholderReplacement() throws SQLException {
        Flyway flyway = Flyway.configure()
                .dataSource(URL, USER, PASSWORD)
                .locations(CLICKHOUSE_FEATURES)
                .defaultSchema(DEFAULT_SCHEMA)
                .load();

        flyway.migrate();

        assertThat(getAllTablesNames()).contains("ch_datatypes");
        assertThat(getAllTablesNames()).contains("ch_repeatable_log");
        assertThat(getAllViewsNames()).contains("ch_active_view");
    }

    @Test
    @DisplayName("ClickHouse Features: Repeatable migration (R__) is executed")
    void repeatableMigration() throws SQLException {
        Flyway flyway = Flyway.configure()
                .dataSource(URL, USER, PASSWORD)
                .locations(CLICKHOUSE_FEATURES)
                .defaultSchema(DEFAULT_SCHEMA)
                .load();

        flyway.migrate();

        assertThat(getAllTablesNames()).contains("ch_repeatable_log");

        int logCount = jdbcTemplate.queryForInt(
                "SELECT COUNT() FROM " + DEFAULT_SCHEMA + ".ch_repeatable_log");
        assertThat(logCount).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("ClickHouse Features: Flyway info shows correct migration states")
    void infoShowsMigrationStates() throws SQLException {
        Flyway flyway = Flyway.configure()
                .dataSource(URL, USER, PASSWORD)
                .locations(CLICKHOUSE_FEATURES)
                .defaultSchema(DEFAULT_SCHEMA)
                .load();

        flyway.migrate();

        MigrationInfoService info = flyway.info();
        MigrationInfo[] applied = info.applied();
        assertThat(applied.length).isGreaterThanOrEqualTo(4);

        for (MigrationInfo migration : applied) {
            assertThat(migration.getState()).isIn(MigrationState.SUCCESS, MigrationState.OUT_OF_ORDER);
        }
    }

    @Test
    @DisplayName("ClickHouseSchema: exists returns true for default schema")
    void schemaExists() throws SQLException {
        ClickHouseSchema schema = createSchema(DEFAULT_SCHEMA);
        assertThat(schema.exists()).isTrue();
    }

    @Test
    @DisplayName("ClickHouseSchema: exists returns false for non-existent schema")
    void schemaDoesNotExist() throws SQLException {
        ClickHouseSchema schema = createSchema("non_existent_schema_xyz");
        assertThat(schema.exists()).isFalse();
    }

    @Test
    @DisplayName("ClickHouseSchema: empty returns true for empty schema")
    void schemaEmpty() throws SQLException {
        assertThat(getAllTablesNames()).isEmpty();
        ClickHouseSchema schema = createSchema(DEFAULT_SCHEMA);
        assertThat(schema.empty()).isTrue();
    }

    @Test
    @DisplayName("ClickHouseSchema: empty returns false when tables exist")
    void schemaNotEmpty() throws SQLException {
        jdbcTemplate.execute("CREATE TABLE " + DEFAULT_SCHEMA + ".schema_test_table(id Int32) ENGINE = MergeTree() ORDER BY id");
        ClickHouseSchema schema = createSchema(DEFAULT_SCHEMA);
        assertThat(schema.empty()).isFalse();
    }

    @Test
    @DisplayName("ClickHouseSchema: create creates a new schema")
    void schemaCreate() throws SQLException {
        ClickHouseSchema schema = createSchema("test_schema_ch");
        jdbcTemplate.execute("DROP DATABASE IF EXISTS test_schema_ch");
        assertThat(schema.exists()).isFalse();

        schema.create();
        assertThat(schema.exists()).isTrue();
    }

    @Test
    @DisplayName("ClickHouseSchema: drop removes the schema")
    void schemaDrop() throws SQLException {
        jdbcTemplate.execute("CREATE DATABASE IF NOT EXISTS test_schema_ch");
        ClickHouseSchema schema = createSchema("test_schema_ch");
        assertThat(schema.exists()).isTrue();

        schema.drop();
        assertThat(schema.exists()).isFalse();
    }

    @Test
    @DisplayName("ClickHouseSchema: clean removes all tables")
    void schemaClean() throws SQLException {
        jdbcTemplate.execute("CREATE TABLE " + DEFAULT_SCHEMA + ".schema_test_table(id Int32) ENGINE = MergeTree() ORDER BY id");
        jdbcTemplate.execute("CREATE TABLE " + DEFAULT_SCHEMA + ".schema_test_table2(name String) ENGINE = MergeTree() ORDER BY name");
        assertThat(getAllTablesNames()).contains("schema_test_table", "schema_test_table2");

        ClickHouseSchema schema = createSchema(DEFAULT_SCHEMA);
        schema.clean();
        assertThat(schema.empty()).isTrue();
        assertThat(getAllTablesNames()).isEmpty();
    }

    @Test
    @DisplayName("ClickHouseSchema: allTables returns all tables")
    void schemaAllTables() throws SQLException {
        jdbcTemplate.execute("CREATE TABLE " + DEFAULT_SCHEMA + ".schema_test_table(id Int32) ENGINE = MergeTree() ORDER BY id");
        jdbcTemplate.execute("CREATE TABLE " + DEFAULT_SCHEMA + ".schema_test_table2(name String) ENGINE = MergeTree() ORDER BY name");

        ClickHouseSchema schema = createSchema(DEFAULT_SCHEMA);
        ClickHouseTable[] tables = schema.allTables();
        List<String> tableNames = Arrays.stream(tables)
                .map(Table::getName)
                .collect(Collectors.toList());

        assertThat(tableNames).contains("schema_test_table", "schema_test_table2");
    }

    @Test
    @DisplayName("ClickHouseSchema: getTable returns a Table object")
    void schemaGetTable() throws SQLException {
        jdbcTemplate.execute("CREATE TABLE " + DEFAULT_SCHEMA + ".schema_test_table(id Int32) ENGINE = MergeTree() ORDER BY id");

        ClickHouseSchema schema = createSchema(DEFAULT_SCHEMA);
        Table table = schema.getTable("schema_test_table");
        assertThat(table).isNotNull();
        assertThat(table.getName()).isEqualTo("schema_test_table");
        assertThat(table.exists()).isTrue();
    }

    @Test
    @DisplayName("ClickHouseSchema: getTable for non-existent table returns exists=false")
    void schemaGetTableNotExists() throws SQLException {
        ClickHouseSchema schema = createSchema(DEFAULT_SCHEMA);
        Table table = schema.getTable("non_existent_table_xyz");
        assertThat(table).isNotNull();
        assertThat(table.getName()).isEqualTo("non_existent_table_xyz");
        assertThat(table.exists()).isFalse();
    }

    @Test
    @DisplayName("ClickHouseTable: exists returns true after creation")
    void tableExists() throws SQLException {
        jdbcTemplate.execute("CREATE TABLE " + DEFAULT_SCHEMA + ".table_test_tbl(id Int32) ENGINE = MergeTree() ORDER BY id");
        ClickHouseTable table = createTable("table_test_tbl");
        assertThat(table.exists()).isTrue();
    }

    @Test
    @DisplayName("ClickHouseTable: exists returns false for non-existent table")
    void tableDoesNotExist() throws SQLException {
        ClickHouseTable table = createTable("non_existent_table_xyz");
        assertThat(table.exists()).isFalse();
    }

    @Test
    @DisplayName("ClickHouseTable: drop removes the table")
    void tableDrop() throws SQLException {
        jdbcTemplate.execute("CREATE TABLE " + DEFAULT_SCHEMA + ".table_test_tbl(id Int32) ENGINE = MergeTree() ORDER BY id");
        assertThat(getAllTablesNames()).contains("table_test_tbl");

        ClickHouseTable table = createTable("table_test_tbl");
        table.drop();
        assertThat(table.exists()).isFalse();
        assertThat(getAllTablesNames()).doesNotContain("table_test_tbl");
    }

    @Test
    @DisplayName("ClickHouseTable: lock does not throw (ClickHouse has no locking)")
    void tableLock() throws SQLException {
        jdbcTemplate.execute("CREATE TABLE " + DEFAULT_SCHEMA + ".table_test_tbl(id Int32) ENGINE = MergeTree() ORDER BY id");

        ClickHouseTable table = createTable("table_test_tbl");
        table.lock();
    }

    @Test
    @DisplayName("Flyway Clean: removes user tables")
    void cleanRemovesUserTables() throws SQLException {
        Flyway flyway = Flyway.configure()
                .dataSource(URL, USER, PASSWORD)
                .locations(INITIAL_MIGRATION_LOCATION)
                .defaultSchema(DEFAULT_SCHEMA)
                .cleanDisabled(false)
                .load();

        flyway.migrate();

        jdbcTemplate.execute("CREATE TABLE " + DEFAULT_SCHEMA + ".clean_test_table(id Int32) ENGINE = MergeTree() ORDER BY id");

        assertThat(getAllTablesNames()).contains("clean_test_table", "some_table", "flyway_schema_history");

        flyway.clean();

        List<String> tablesAfterClean = getAllTablesNames();
        assertThat(tablesAfterClean).doesNotContain("some_table", "clean_test_table");
    }

    @Test
    @DisplayName("Flyway Repair: fixes schema history after successful migration")
    void repairFixesFailedMigration() throws SQLException {
        Flyway flyway = Flyway.configure()
                .dataSource(URL, USER, PASSWORD)
                .locations(INITIAL_MIGRATION_LOCATION)
                .defaultSchema(DEFAULT_SCHEMA)
                .load();

        flyway.migrate();
        assertThat(flyway.info().applied().length).isEqualTo(2);

        flyway.repair();

        assertThat(getAllTablesNames()).contains("flyway_schema_history");
    }

    private static ClickHouseSchema createSchema(String schemaName) throws SQLException {
        return new ClickHouseSchema(jdbcTemplate, createDatabase(), schemaName);
    }

    private static ClickHouseTable createTable(String tableName) throws SQLException {
        ClickHouseSchema schema = createSchema(DEFAULT_SCHEMA);
        return new ClickHouseTable(jdbcTemplate, createDatabase(), schema, tableName);
    }

    private static ClickHouseDatabase createDatabase() throws SQLException {
        ClassicConfiguration config = new ClassicConfiguration();
        config.setUrl(URL);
        config.setUser(USER);
        config.setPassword(PASSWORD);
        DataSource ds = Flyway.configure().dataSource(URL, USER, PASSWORD).getDataSource();
        config.setDataSource(ds);
        config.setDefaultSchema(DEFAULT_SCHEMA);
        config.setSchemas(new String[]{DEFAULT_SCHEMA});

        NoOpStatementInterceptor noOpInterceptor = new NoOpStatementInterceptor();
        JdbcConnectionFactory connectionFactory = new JdbcConnectionFactory(
                ds,
                config,
                noOpInterceptor
        );

        return new ClickHouseDatabase(config, connectionFactory, noOpInterceptor);
    }

    private static List<String> getAllTablesNames() throws SQLException {
        return jdbcTemplate.queryForStringList(
                "SELECT name FROM system.tables WHERE database = ? AND engine NOT LIKE '%View%'",
                DEFAULT_SCHEMA);
    }

    private static List<String> getAllViewsNames() throws SQLException {
        return jdbcTemplate.queryForStringList(
                "SELECT name FROM system.tables WHERE database = ? AND engine = 'View'",
                DEFAULT_SCHEMA);
    }

    private static Integer countSomeTableRows() throws SQLException {
        return jdbcTemplate.queryForInt(
                "SELECT COUNT() FROM " + DEFAULT_SCHEMA + ".some_table");
    }

    private static List<String> getFlywayHistoryMigrationDescriptions() throws SQLException {
        return jdbcTemplate.queryForStringList(
                "SELECT description FROM " + DEFAULT_SCHEMA + ".flyway_schema_history ORDER BY installed_rank");
    }
}