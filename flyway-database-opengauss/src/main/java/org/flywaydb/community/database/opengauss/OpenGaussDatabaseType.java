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

import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.internal.database.base.Database;
import org.flywaydb.core.internal.jdbc.JdbcConnectionFactory;
import org.flywaydb.core.internal.jdbc.StatementInterceptor;
import org.flywaydb.core.internal.util.ClassUtils;
import org.flywaydb.database.mysql.MySQLDatabaseType;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;

public class OpenGaussDatabaseType extends MySQLDatabaseType {

    private static final String OPENGAUSS_JDBC_DRIVER = "org.opengauss.Driver";
    private static final String OPENGAUSS_LEGACY_JDBC_DRIVER = "org.postgresql.Driver";

    @Override
    public String getName() {
        return "OpenGauss";
    }

    @Override
    public int getPriority() {
        // OpenGauss needs to be checked in advance of both MySQL and PostgreSQL
        return 2;
    }

    @Override
    public int getNullType() {
        return Types.VARCHAR;
    }

    @Override
    public boolean handlesJDBCUrl(String url) {
        return url.startsWith("jdbc:mysql:") || url.startsWith("jdbc:opengauss:");
    }

    @Override
    public String getDriverClass(String url, ClassLoader classLoader) {
        return url.startsWith("jdbc:opengauss:") ?
            OPENGAUSS_JDBC_DRIVER :
            super.getDriverClass(url, classLoader);
    }

    @Override
    public String getBackupDriverClass(String url, ClassLoader classLoader) {
        if (url.startsWith("jdbc:opengauss:") && ClassUtils.isPresent(OPENGAUSS_LEGACY_JDBC_DRIVER, classLoader)) {
            return OPENGAUSS_LEGACY_JDBC_DRIVER;
        }
        return super.getBackupDriverClass(url, classLoader);
    }

    @Override
    public boolean handlesDatabaseProductNameAndVersion(String databaseProductName, String databaseProductVersion, Connection connection) {
        if (!databaseProductName.contains("MySQL") && !databaseProductName.contains("openGauss") && !databaseProductName.contains("PostgreSQL")) {
            return false;
        }

        String versionComment;
        try {
            versionComment = OpenGaussJdbcUtils.getVersionComment(connection);
        } catch (SQLException e) {
            return false;
        }
        return versionComment != null && versionComment.contains("openGauss");
    }

    @Override
    public Database createDatabase(Configuration configuration, JdbcConnectionFactory jdbcConnectionFactory, StatementInterceptor statementInterceptor) {
        return new OpenGaussDatabase(configuration, jdbcConnectionFactory, statementInterceptor);
    }
}