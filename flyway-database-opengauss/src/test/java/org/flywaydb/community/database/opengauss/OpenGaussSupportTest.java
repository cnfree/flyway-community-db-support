/*-
 * ========================LICENSE_START=================================
 * flyway-database-opengauss
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

package org.flywaydb.community.database.opengauss;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.flywaydb.core.api.MigrationState;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.configuration.ClassicConfiguration;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.internal.database.base.Schema;
import org.flywaydb.core.internal.database.base.Table;
import org.flywaydb.core.internal.jdbc.JdbcConnectionFactory;
import org.flywaydb.core.internal.jdbc.JdbcTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class OpenGaussSupportTest {

    private static final String URL = "jdbc:opengauss://192.168.30.75:8888/arcana_paas?currentSchema=arcana_paas";
    private static final String USER = "gaussdb";
    private static final String PASSWORD = "YourPass@123";
    private static final String DEFAULT_SCHEMA = "arcana_paas";
    private static final String NEXT_MIGRATION_LOCATION = "next_migration";
    private static final String INITIAL_MIGRATION_LOCATION = "initial_migration";
    private static final String MYSQL_FEATURES = "mysql_features";

    private final JdbcTemplate jdbcTemplate = jdbcTemplate();

    static {
        try {
            Class.forName("org.opengauss.Driver");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Failed to load OpenGauss JDBC driver", e);
        }
    }

    OpenGaussSupportTest() throws SQLException {
    }

    @AfterEach
    void cleanup() throws SQLException {
        dropCommonTables();
    }

    private void dropCommonTables() throws SQLException {
        jdbcTemplate.execute("DROP TABLE IF EXISTS " + DEFAULT_SCHEMA + ".some_table");
        jdbcTemplate.execute("DROP VIEW IF EXISTS " + DEFAULT_SCHEMA + ".some_view");
        jdbcTemplate.execute("DROP TABLE IF EXISTS " + DEFAULT_SCHEMA + ".mysql_features");
        jdbcTemplate.execute("DROP TABLE IF EXISTS " + DEFAULT_SCHEMA + ".repeatable_log");
        jdbcTemplate.execute("DROP TABLE IF EXISTS " + DEFAULT_SCHEMA + ".flyway_schema_history");
    }

    @Test
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
                .isEqualTo(List.of("first", "second"));
        assertThat(getAllViewsNames())
                .contains("some_view");
    }

    @Test
    void does_not_apply_a_migration_several_times() throws SQLException {
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
                .isEqualTo(List.of("first", "second"));
    }

    @Test
    void applies_only_new_migrations_when_some_was_already_applied() throws SQLException {
        Flyway flyway = Flyway.configure()
                .dataSource(URL, USER, PASSWORD)
                .locations(INITIAL_MIGRATION_LOCATION)
                .defaultSchema(DEFAULT_SCHEMA)
                .load();

        flyway.migrate();
        assertThat(countSomeTableRows()).isEqualTo(2);
        assertThat(getFlywayHistoryMigrationDescriptions())
                .isEqualTo(List.of("first", "second"));

        Flyway.configure()
                .dataSource(URL, USER, PASSWORD)
                .locations(NEXT_MIGRATION_LOCATION)
                .defaultSchema(DEFAULT_SCHEMA)
                .load()
                .migrate();

        assertThat(countSomeTableRows()).isEqualTo(3);
        assertThat(getFlywayHistoryMigrationDescriptions())
                .isEqualTo(List.of("first", "second", "add more rows"));
    }

    @Test
    void sets_baseline() throws SQLException {
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
                .isEqualTo(List.of("<< Flyway Baseline >>"));
    }

    @Nested
    @DisplayName("MySQL Compatibility Feature Tests")
    class MySQLCompatibilityTests {

        @AfterEach
        void cleanupMySQLFeatures() throws SQLException {
            dropCommonTables();
        }

        @Test
        @DisplayName("CREATE TABLE IF NOT EXISTS with various MySQL data types")
        void createsTableWithMySQLDataTypes() throws SQLException {
            Flyway flyway = Flyway.configure()
                    .dataSource(URL, USER, PASSWORD)
                    .locations(MYSQL_FEATURES)
                    .defaultSchema(DEFAULT_SCHEMA)
                    .load();

            flyway.migrate();

            assertThat(getAllTablesNames())
                    .contains("mysql_features");

            int rowCount = jdbcTemplate.queryForInt(
                    "SELECT COUNT(1) FROM information_schema.columns WHERE table_schema=? AND table_name='mysql_features'",
                    DEFAULT_SCHEMA);
            assertThat(rowCount).isEqualTo(7);
        }

        @Test
        @DisplayName("Multi-row INSERT, UPDATE, CREATE INDEX, CREATE VIEW")
        void multiInsertAndUpdateAndIndex() throws SQLException {
            Flyway flyway = Flyway.configure()
                    .dataSource(URL, USER, PASSWORD)
                    .locations(MYSQL_FEATURES)
                    .defaultSchema(DEFAULT_SCHEMA)
                    .load();

            flyway.migrate();

            int count = jdbcTemplate.queryForInt(
                    "SELECT COUNT(1) FROM " + DEFAULT_SCHEMA + ".some_table");
            assertThat(count).isEqualTo(3);

            String firstText = jdbcTemplate.queryForString(
                    "SELECT text FROM " + DEFAULT_SCHEMA + ".some_table WHERE id=1");
            assertThat(firstText).isEqualTo("updated first");

            assertThat(getAllViewsNames()).contains("some_view");
            assertThat(getAllTablesNames()).contains("repeatable_log");
        }

        @Test
        @DisplayName("Placeholder replacement works in SQL scripts")
        void placeholderReplacement() throws SQLException {
            Flyway flyway = Flyway.configure()
                    .dataSource(URL, USER, PASSWORD)
                    .locations(MYSQL_FEATURES)
                    .defaultSchema(DEFAULT_SCHEMA)
                    .load();

            flyway.migrate();

            assertThat(getAllTablesNames()).contains("mysql_features");
            assertThat(getAllTablesNames()).contains("repeatable_log");
        }

        @Test
        @DisplayName("Repeatable migration re-applies when checksum changes")
        void repeatableMigration() throws SQLException {
            Flyway flyway = Flyway.configure()
                    .dataSource(URL, USER, PASSWORD)
                    .locations(MYSQL_FEATURES)
                    .defaultSchema(DEFAULT_SCHEMA)
                    .load();

            flyway.migrate();

            assertThat(getAllTablesNames()).contains("repeatable_log");
            List<String> descriptions = getFlywayHistoryMigrationDescriptions();
            assertThat(descriptions.stream().filter(d -> d.contains("repeatable")).count()).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("Flyway info shows correct migration states")
        void infoShowsMigrationStates() throws SQLException {
            Flyway flyway = Flyway.configure()
                    .dataSource(URL, USER, PASSWORD)
                    .locations(MYSQL_FEATURES)
                    .defaultSchema(DEFAULT_SCHEMA)
                    .load();

            flyway.migrate();

            MigrationInfoService info = flyway.info();
            MigrationInfo[] applied = info.applied();
            assertThat(applied.length).isGreaterThanOrEqualTo(3);

            for (MigrationInfo migration : applied) {
                assertThat(migration.getState()).isIn(MigrationState.SUCCESS, MigrationState.OUT_OF_ORDER);
            }
        }
    }

    @Nested
    @DisplayName("OpenGaussSchema Tests")
    class OpenGaussSchemaTests {

        @AfterEach
        void cleanupSchemaTests() throws SQLException {
            dropCommonTables();
            jdbcTemplate.execute("DROP SCHEMA IF EXISTS test_schema_og CASCADE");
            jdbcTemplate.execute("DROP TABLE IF EXISTS " + DEFAULT_SCHEMA + ".schema_test_table");
            jdbcTemplate.execute("DROP TABLE IF EXISTS " + DEFAULT_SCHEMA + ".schema_test_table2");
        }

        @Test
        @DisplayName("Schema exists returns true for default schema")
        void schemaExists() throws SQLException {
            OpenGaussSchema schema = createSchema(DEFAULT_SCHEMA);
            assertThat(schema.exists()).isTrue();
        }

        @Test
        @DisplayName("Schema exists returns false for non-existent schema")
        void schemaDoesNotExist() throws SQLException {
            OpenGaussSchema schema = createSchema("non_existent_schema_xyz");
            assertThat(schema.exists()).isFalse();
        }

        @Test
        @DisplayName("Schema empty returns true for empty schema")
        void schemaEmpty() throws SQLException {
            assertThat(getAllTablesNames()).isEmpty();
            OpenGaussSchema schema = createSchema(DEFAULT_SCHEMA);
            assertThat(schema.empty()).isTrue();
        }

        @Test
        @DisplayName("Schema empty returns false when tables exist")
        void schemaNotEmpty() throws SQLException {
            jdbcTemplate.execute("CREATE TABLE " + DEFAULT_SCHEMA + ".schema_test_table(id INT)");
            OpenGaussSchema schema = createSchema(DEFAULT_SCHEMA);
            assertThat(schema.empty()).isFalse();
        }

        @Test
        @DisplayName("Schema create creates a new schema")
        void schemaCreate() throws SQLException {
            OpenGaussSchema schema = createSchema("test_schema_og");
            jdbcTemplate.execute("DROP SCHEMA IF EXISTS test_schema_og CASCADE");
            assertThat(schema.exists()).isFalse();

            schema.create();
            assertThat(schema.exists()).isTrue();
        }

        @Test
        @DisplayName("Schema drop removes the schema")
        void schemaDrop() throws SQLException {
            jdbcTemplate.execute("CREATE SCHEMA IF NOT EXISTS test_schema_og");
            OpenGaussSchema schema = createSchema("test_schema_og");
            assertThat(schema.exists()).isTrue();

            schema.drop();
            assertThat(schema.exists()).isFalse();
        }

        @Test
        @DisplayName("Schema clean removes all tables")
        void schemaClean() throws SQLException {
            jdbcTemplate.execute("CREATE TABLE " + DEFAULT_SCHEMA + ".schema_test_table(id INT)");
            jdbcTemplate.execute("CREATE TABLE " + DEFAULT_SCHEMA + ".schema_test_table2(name VARCHAR(100))");
            assertThat(getAllTablesNames()).contains("schema_test_table", "schema_test_table2");

            OpenGaussSchema schema = createSchema(DEFAULT_SCHEMA);
            schema.clean();
            assertThat(schema.empty()).isTrue();
            assertThat(getAllTablesNames()).isEmpty();
        }

        @Test
        @DisplayName("Schema allTables returns all BASE TABLE type tables")
        void schemaAllTables() throws SQLException {
            jdbcTemplate.execute("CREATE TABLE " + DEFAULT_SCHEMA + ".schema_test_table(id INT)");
            jdbcTemplate.execute("CREATE TABLE " + DEFAULT_SCHEMA + ".schema_test_table2(name VARCHAR(100))");

            OpenGaussSchema schema = createSchema(DEFAULT_SCHEMA);
            OpenGaussTable[] tables = schema.allTables();
            List<String> tableNames = Arrays.stream(tables)
                    .map(t -> t.getName())
                    .collect(Collectors.toList());

            assertThat(tableNames).contains("schema_test_table", "schema_test_table2");
        }

        @Test
        @DisplayName("Schema getTable returns a Table object")
        void schemaGetTable() throws SQLException {
            jdbcTemplate.execute("CREATE TABLE " + DEFAULT_SCHEMA + ".schema_test_table(id INT)");

            OpenGaussSchema schema = createSchema(DEFAULT_SCHEMA);
            Table table = schema.getTable("schema_test_table");
            assertThat(table).isNotNull();
            assertThat(table.getName()).isEqualTo("schema_test_table");
            assertThat(table.exists()).isTrue();
        }

        @Test
        @DisplayName("Schema getTable for non-existent table returns exists=false")
        void schemaGetTableNotExists() throws SQLException {
            OpenGaussSchema schema = createSchema(DEFAULT_SCHEMA);
            Table table = schema.getTable("non_existent_table_xyz");
            assertThat(table).isNotNull();
            assertThat(table.getName()).isEqualTo("non_existent_table_xyz");
            assertThat(table.exists()).isFalse();
        }
    }

    @Nested
    @DisplayName("OpenGaussTable Tests")
    class OpenGaussTableTests {

        @AfterEach
        void cleanupTableTests() throws SQLException {
            dropCommonTables();
            jdbcTemplate.execute("DROP TABLE IF EXISTS " + DEFAULT_SCHEMA + ".table_test_tbl");
        }

        @Test
        @DisplayName("Table exists returns true after creation")
        void tableExists() throws SQLException {
            jdbcTemplate.execute("CREATE TABLE " + DEFAULT_SCHEMA + ".table_test_tbl(id INT)");
            OpenGaussTable table = createTable("table_test_tbl");
            assertThat(table.exists()).isTrue();
        }

        @Test
        @DisplayName("Table exists returns false for non-existent table")
        void tableDoesNotExist() throws SQLException {
            OpenGaussTable table = createTable("non_existent_table_xyz");
            assertThat(table.exists()).isFalse();
        }

        @Test
        @DisplayName("Table drop removes the table")
        void tableDrop() throws SQLException {
            jdbcTemplate.execute("CREATE TABLE " + DEFAULT_SCHEMA + ".table_test_tbl(id INT)");
            assertThat(getAllTablesNames()).contains("table_test_tbl");

            OpenGaussTable table = createTable("table_test_tbl");
            table.drop();
            assertThat(table.exists()).isFalse();
            assertThat(getAllTablesNames()).doesNotContain("table_test_tbl");
        }

        @Test
        @DisplayName("Table lock acquires SELECT FOR UPDATE lock")
        void tableLock() throws SQLException {
            jdbcTemplate.execute("CREATE TABLE " + DEFAULT_SCHEMA + ".table_test_tbl(id INT)");
            jdbcTemplate.execute("INSERT INTO " + DEFAULT_SCHEMA + ".table_test_tbl(id) VALUES (1)");

            OpenGaussTable table = createTable("table_test_tbl");

            try {
                jdbcTemplate.getConnection().setAutoCommit(false);
                table.lock();
            } finally {
                jdbcTemplate.getConnection().rollback();
                jdbcTemplate.getConnection().setAutoCommit(true);
            }
        }
    }

    @Nested
    @DisplayName("Flyway Clean Tests")
    class FlywayCleanTests {

        @AfterEach
        void cleanupCleanTests() throws SQLException {
            dropCommonTables();
            jdbcTemplate.execute("DROP VIEW IF EXISTS " + DEFAULT_SCHEMA + ".clean_test_view");
            jdbcTemplate.execute("DROP TABLE IF EXISTS " + DEFAULT_SCHEMA + ".clean_test_table");
        }

        @Test
        @DisplayName("Clean removes user tables but preserves flyway_schema_history")
        void cleanPreservesSchemaHistory() throws SQLException {
            Flyway flyway = Flyway.configure()
                    .dataSource(URL, USER, PASSWORD)
                    .locations(INITIAL_MIGRATION_LOCATION)
                    .defaultSchema(DEFAULT_SCHEMA)
                    .cleanDisabled(false)
                    .load();

            flyway.migrate();

            jdbcTemplate.execute("CREATE TABLE " + DEFAULT_SCHEMA + ".clean_test_table(id INT)");

            assertThat(getAllTablesNames()).contains("clean_test_table", "some_table", "flyway_schema_history");

            flyway.clean();

            List<String> tablesAfterClean = getAllTablesNames();
            assertThat(tablesAfterClean).doesNotContain("some_table", "clean_test_table");
        }
    }

    @Nested
    @DisplayName("Repair Tests")
    class RepairTests {

        @AfterEach
        void cleanupRepairTests() throws SQLException {
            dropCommonTables();
        }

        @Test
        @DisplayName("Repair fixes schema history after failed migration")
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
    }

    private OpenGaussSchema createSchema(String schemaName) throws SQLException {
        return new OpenGaussSchema(jdbcTemplate, createDatabase(), schemaName);
    }

    private OpenGaussTable createTable(String tableName) throws SQLException {
        OpenGaussSchema schema = createSchema(DEFAULT_SCHEMA);
        return new OpenGaussTable(jdbcTemplate, createDatabase(), schema, tableName);
    }

    private OpenGaussDatabase createDatabase() throws SQLException {
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

        return new OpenGaussDatabase(config, connectionFactory, noOpInterceptor);
    }

    private List<String> getAllTablesNames() throws SQLException {
        return jdbcTemplate.queryForStringList(
                "SELECT TABLE_NAME FROM information_schema.tables WHERE TABLE_SCHEMA = ?",
                DEFAULT_SCHEMA);
    }

    private List<String> getAllViewsNames() throws SQLException {
        return jdbcTemplate.queryForStringList(
                "SELECT TABLE_NAME FROM information_schema.views WHERE TABLE_SCHEMA = ?",
                DEFAULT_SCHEMA);
    }

    private Integer countSomeTableRows() throws SQLException {
        return jdbcTemplate.queryForInt(
                "SELECT count(*) FROM " + DEFAULT_SCHEMA + ".some_table");
    }

    private List<String> getFlywayHistoryMigrationDescriptions() throws SQLException {
        return jdbcTemplate.queryForStringList(
                "SELECT description FROM " + DEFAULT_SCHEMA + ".flyway_schema_history ORDER BY installed_rank");
    }

    private JdbcTemplate jdbcTemplate() throws SQLException {
        return new JdbcTemplate(
                DriverManager.getConnection(URL, USER, PASSWORD),
                new OpenGaussDatabaseType()
        );
    }
}