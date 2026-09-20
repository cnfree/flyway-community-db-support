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

import org.flywaydb.core.internal.util.StringUtils;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class OpenGaussJdbcUtils {

    public static String getVersionComment(Connection connection) throws SQLException {
        // First try SELECT version() which works with PostgreSQL protocol
        String version = queryVersion(connection);
        if (StringUtils.hasText(version)) {
            return version;
        }
        // Fallback to MySQL-style SHOW VARIABLES for MySQL compatibility mode
        return queryVariable(connection, "version_comment");
    }

    public static String getVersionNumber(Connection connection) throws SQLException {
        String versionComment = getVersionComment(connection);
        if (StringUtils.hasText(versionComment)) {
            // openGauss version from SELECT version() format: "PostgreSQL x.x.x (openGauss x.x.x ...)"
            // Or from SHOW VARIABLES format: "openGauss x.x.x ..."
            if (versionComment.contains("(") && versionComment.contains(")")) {
                String insideBrackets = versionComment.substring(
                    versionComment.indexOf('(') + 1,
                    versionComment.lastIndexOf(')'));
                String[] parts = insideBrackets.split(" ");
                for (int i = 0; i < parts.length - 1; i++) {
                    if ("openGauss".equalsIgnoreCase(parts[i])) {
                        return parts[i + 1];
                    }
                }
            }
            String[] parts = versionComment.split(" ");
            if (parts.length > 1) {
                return parts[1];
            }
        }
        return null;
    }

    private static String queryVersion(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            ResultSet rs = statement.executeQuery("SELECT version()");
            if (rs.next()) {
                return rs.getString(1);
            }
        }
        return null;
    }

    private static String queryVariable(Connection connection, String variable) throws SQLException {
        assert StringUtils.hasText(variable);
        String sql = String.format("SHOW VARIABLES LIKE '%s'", variable);
        try (Statement statement = connection.createStatement()) {
            ResultSet rs = statement.executeQuery(sql);
            if (rs.next()) {
                return rs.getString("VALUE");
            }
        }
        return null;
    }
}