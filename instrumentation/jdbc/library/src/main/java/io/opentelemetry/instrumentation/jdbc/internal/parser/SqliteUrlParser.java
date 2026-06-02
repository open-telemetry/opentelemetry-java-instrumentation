/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal.parser;

/**
 * Parser for SQLite Database JDBC URLs.
 *
 * <p>Sample URLs:
 *
 * <ul>
 *   <li>sqlite:memory: (in-memory)
 *   <li>sqlite: (in-memory, alternative syntax)
 *   <li>sqlite:file:myDB?mode=memory (in-memory, alternative syntax)
 *   <li>sqlite:/path/to/db (file-based)
 *   <li>sqlite:resource:db (from classpath resource)
 * </ul>
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class SqliteUrlParser implements JdbcUrlParser {

  // copied from DbIncubatingAttributes.DbSystemNameIncubatingValues
  private static final String SQLITE = "sqlite";

  public static final SqliteUrlParser INSTANCE = new SqliteUrlParser();

  private SqliteUrlParser() {}

  @Override
  public void parse(String jdbcUrl, ParseContext ctx) {
    ctx.system(SQLITE);

    ctx.applyUserProperty();

    // sqlite is always local, so we don't need to check for host/port
    ctx.host(null);
    ctx.port(null);

    String sqliteUrl = jdbcUrl.substring("sqlite:".length());
    // ignore query parameters, if any
    int queryIndex = sqliteUrl.indexOf('?');
    if (queryIndex != -1) {
      sqliteUrl = sqliteUrl.substring(0, queryIndex);
    }

    if ("memory:".equals(sqliteUrl) || "".equals(sqliteUrl)) {
      // typical in-memory URL is "sqlite:memory:", but "sqlite:" is also supported
      ctx.subtype("memory");
    } else if (sqliteUrl.startsWith("file:") && jdbcUrl.contains("mode=memory")) {
      // in-memory database specified using "file:" syntax with ?mode=memory"
      ctx.subtype("memory");
      String filePath = sqliteUrl.substring("file:".length());
      if (!filePath.isEmpty()) {
        ctx.databaseName(filePath);
      }
    } else if (sqliteUrl.startsWith("resource:")) {
      // database loaded from classpath resource, e.g. "sqlite:resource:db/mydb.db"
      ctx.subtype("resource");
      String resourcePath = sqliteUrl.substring("resource:".length());
      // Use the last segment of the resource path as the database name, if available
      if (!resourcePath.isEmpty()) {
        int dbNameStartLocation = resourcePath.lastIndexOf('/');
        if (dbNameStartLocation != -1) {
          ctx.databaseName(resourcePath.substring(dbNameStartLocation + 1));
        } else {
          ctx.databaseName(resourcePath);
        }
      }
    } else {
      ctx.subtype("file");
      // Use the last segment of the file path as the database name, if available
      int dbNameStartLocation = sqliteUrl.lastIndexOf('/');
      if (dbNameStartLocation != -1) {
        ctx.databaseName(sqliteUrl.substring(dbNameStartLocation + 1));
      } else {
        ctx.databaseName(sqliteUrl);
      }
    }
  }
}
