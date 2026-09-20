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

import org.flywaydb.core.internal.database.base.Schema;
import org.flywaydb.core.internal.database.base.Table;
import org.flywaydb.core.internal.jdbc.JdbcTemplate;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class OpenGaussSchema extends Schema<OpenGaussDatabase, OpenGaussTable> {

    OpenGaussSchema(JdbcTemplate jdbcTemplate, OpenGaussDatabase database, String name) {
        super(jdbcTemplate, database, name);
    }

    @Override
    protected boolean doExists() throws SQLException {
        return jdbcTemplate.queryForInt(
                "SELECT COUNT(1) FROM information_schema.schemata WHERE schema_name=?",
                name) > 0;
    }

    @Override
    protected boolean doEmpty() throws SQLException {
        return jdbcTemplate.queryForInt(
                "SELECT COUNT(1) FROM ("
                        + "SELECT table_name FROM information_schema.tables WHERE table_schema=? "
                        + "UNION ALL "
                        + "SELECT table_name FROM information_schema.views WHERE table_schema=?"
                        + ") t",
                name, name) == 0;
    }

    @Override
    protected void doCreate() throws SQLException {
        jdbcTemplate.execute("CREATE SCHEMA " + database.quote(name));
    }

    @Override
    protected void doDrop() throws SQLException {
        jdbcTemplate.execute("DROP SCHEMA " + database.quote(name) + " CASCADE");
    }

    @Override
    protected void doClean() throws SQLException {
        for (Table table : allTables()) {
            table.drop();
        }
    }

    @Override
    protected OpenGaussTable[] doAllTables() throws SQLException {
        List<String> tableNames = jdbcTemplate.queryForStringList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema=? AND table_type='BASE TABLE'",
                name);

        OpenGaussTable[] tables = new OpenGaussTable[tableNames.size()];
        for (int i = 0; i < tableNames.size(); i++) {
            tables[i] = new OpenGaussTable(jdbcTemplate, database, this, tableNames.get(i));
        }
        return tables;
    }

    @Override
    public Table getTable(String tableName) {
        return new OpenGaussTable(jdbcTemplate, database, this, tableName);
    }
}