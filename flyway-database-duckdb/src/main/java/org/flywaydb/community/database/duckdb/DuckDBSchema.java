package org.flywaydb.community.database.duckdb;

import org.flywaydb.core.internal.database.base.Schema;
import org.flywaydb.core.internal.jdbc.JdbcTemplate;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class DuckDBSchema extends Schema<DuckDBDatabase, DuckDBTable> {

    public DuckDBSchema(JdbcTemplate jdbcTemplate, DuckDBDatabase database, String name) {
        super(jdbcTemplate, database, name);
    }

    @Override
    protected boolean doExists() throws SQLException {
        String countSchemasWithName = "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name = ?";
        return jdbcTemplate.queryForInt(countSchemasWithName, name) > 0;
    }

    @Override
    protected boolean doEmpty() throws SQLException {
        String countTablesInSchema = "SELECT count(*) from information_schema.tables WHERE table_schema = ?";
        return jdbcTemplate.queryForInt(countTablesInSchema, name) == 0;
    }

    @Override
    protected void doCreate() throws SQLException {
        jdbcTemplate.execute("CREATE SCHEMA " + database.quote(name));
    }

    @Override
    protected void doDrop() throws SQLException {
        jdbcTemplate.execute("DROP SCHEMA " + database.quote(name));
    }

    @Override
    protected void doClean() throws SQLException {
        dropAll("MACRO", getAllMacros());
        dropAll("SEQUENCE", getAllObjectsNames("sequence_name", "duckdb_sequences()"));
        dropAll("VIEW", getAllViews());
        dropAll("TABLE", getAllTablesNames());
    }

    @Override
    protected DuckDBTable[] doAllTables() throws SQLException {
        List<String> allTableNames = getAllTablesNames();
        List<DuckDBTable> tables = new ArrayList<>();
        for (String tableName : allTableNames) {
            tables.add(getTable(tableName));
        }
        return tables.toArray(new DuckDBTable[0]);
    }

    @Override
    public DuckDBTable getTable(String tableName) {
        return new DuckDBTable(jdbcTemplate, database, this, tableName);
    }

    private void dropAll(String objectType, List<String> objectsNames) throws SQLException {
        for (String objectName : objectsNames) {
            String sql = String.format("DROP %s %s.%s CASCADE", objectType, database.quote(name), database.quote(objectName));
            jdbcTemplate.execute(sql);
        }
    }

    private List<String> getAllMacros() throws SQLException {
        String sql = "SELECT function_name FROM duckdb_functions() WHERE NOT internal AND schema_name = ?";
        return jdbcTemplate.queryForStringList(sql, name);
    }

    private List<String> getAllViews() throws SQLException {
        String sql = "SELECT view_name FROM duckdb_views() WHERE NOT internal AND schema_name = ?";
        return jdbcTemplate.queryForStringList(sql, name);
    }

    private List<String> getAllTablesNames() throws SQLException {
        return getAllObjectsNames("table_name", "duckdb_tables()");
    }

    private List<String> getAllObjectsNames(String catalogNameField, String catalogTable) throws SQLException {
        String sql = String.format("SELECT %s FROM %s WHERE schema_name = ?", catalogNameField, catalogTable);
        return jdbcTemplate.queryForStringList(sql, name);
    }
}
