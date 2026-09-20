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

import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.internal.database.base.Database;
import org.flywaydb.core.internal.database.base.Table;
import org.flywaydb.core.internal.jdbc.JdbcConnectionFactory;
import org.flywaydb.core.internal.jdbc.StatementInterceptor;

import java.sql.Connection;
import java.sql.SQLException;

public class OpenGaussDatabase extends Database<OpenGaussConnection> {

    public OpenGaussDatabase(Configuration configuration, JdbcConnectionFactory jdbcConnectionFactory, StatementInterceptor statementInterceptor) {
        super(configuration, jdbcConnectionFactory, statementInterceptor);
    }

    @Override
    protected OpenGaussConnection doGetConnection(Connection connection) {
        return new OpenGaussConnection(this, connection);
    }

    @Override
    public void ensureSupported() {
        // OpenGauss in MySQL compatibility mode, version from server (e.g. 6.0.5)
        // PostgreSQL protocol driver reports product version as 9.2, but that's not the actual server version
        // We accept all versions since our detection already verifies it's truly OpenGauss
    }

    @Override
    protected MigrationVersion determineVersion() {
        String versionNumber;
        try {
            versionNumber = OpenGaussJdbcUtils.getVersionNumber(rawMainJdbcConnection);
        } catch (SQLException e) {
            throw new FlywayException("Failed to get version number", e);
        }
        if (versionNumber != null) {
            return MigrationVersion.fromVersion(versionNumber);
        }
        try {
            return MigrationVersion.fromVersion(jdbcMetaData.getDatabaseProductVersion());
        } catch (SQLException e) {
            throw new FlywayException("Unable to determine the database version", e);
        }
    }

    @Override
    public boolean supportsDdlTransactions() {
        return true;
    }

    @Override
    public String getBooleanTrue() {
        return "1";
    }

    @Override
    public String getBooleanFalse() {
        return "0";
    }

    @Override
    public boolean catalogIsSchema() {
        return true;
    }

    @Override
    public String getRawCreateScript(Table table, boolean baseline) {
        String baselineMarker = "";
        if (baseline) {
            baselineMarker = ";\n" + getBaselineStatement(table);
        }

        return "CREATE TABLE " + table + " (\n"
                + "    \"installed_rank\" INT NOT NULL,\n"
                + "    \"version\" VARCHAR(50),\n"
                + "    \"description\" VARCHAR(200) NOT NULL,\n"
                + "    \"type\" VARCHAR(20) NOT NULL,\n"
                + "    \"script\" VARCHAR(1000) NOT NULL,\n"
                + "    \"checksum\" INT,\n"
                + "    \"installed_by\" VARCHAR(100) NOT NULL,\n"
                + "    \"installed_on\" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,\n"
                + "    \"execution_time\" INT NOT NULL,\n"
                + "    \"success\" BOOL NOT NULL,\n"
                + "    CONSTRAINT \"" + table.getName() + "_pk\" PRIMARY KEY (\"installed_rank\")\n"
                + ")"
                + baselineMarker
                + ";\n"
                + "CREATE INDEX \"" + table.getName() + "_s_idx\" ON " + table + " (\"success\");";
    }
}