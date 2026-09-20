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

import org.flywaydb.core.api.callback.Callback;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.migration.JavaMigration;
import org.flywaydb.core.api.resource.LoadableResource;
import org.flywaydb.core.extensibility.AppliedMigration;
import org.flywaydb.core.internal.database.base.Database;
import org.flywaydb.core.internal.database.base.Table;
import org.flywaydb.core.internal.jdbc.StatementInterceptor;
import org.flywaydb.core.internal.schemahistory.SchemaHistory;
import org.flywaydb.core.internal.sqlscript.SqlStatement;

import java.sql.Connection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

class NoOpStatementInterceptor implements StatementInterceptor {

    @Override
    public void init(Configuration configuration, Database database, Table table) {
    }

    @Override
    public boolean isConfigured(Configuration configuration) {
        return true;
    }

    @Override
    public List<Callback> getCallbacks() {
        return Collections.emptyList();
    }

    @Override
    public Connection createConnectionProxy(Connection connection) {
        return connection;
    }

    @Override
    public SchemaHistory getSchemaHistory(Configuration configuration, SchemaHistory schemaHistory) {
        return schemaHistory;
    }

    @Override
    public void schemaHistoryTableCreate(boolean baseline) {
    }

    @Override
    public void schemaHistoryTableInsert(AppliedMigration appliedMigration) {
    }

    @Override
    public void close() {
    }

    @Override
    public void sqlScript(LoadableResource resource) {
    }

    @Override
    public void scriptMigration(LoadableResource resource) {
    }

    @Override
    public void javaMigration(JavaMigration javaMigration) {
    }

    @Override
    public void sqlStatement(SqlStatement sqlStatement) {
    }

    @Override
    public void interceptCommand(String command) {
    }

    @Override
    public void interceptStatement(String sql) {
    }

    @Override
    public void interceptPreparedStatement(String sql, Map<Integer, Object> params) {
    }

    @Override
    public void interceptCallableStatement(String sql) {
    }

    @Override
    public void schemaHistoryTableDeleteFailed(Table table, AppliedMigration appliedMigration) {
    }
}