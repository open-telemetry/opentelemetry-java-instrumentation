/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal.parser;

import static io.opentelemetry.instrumentation.jdbc.internal.parser.UrlParsingUtils.splitQuery;

import java.util.Map;

/**
 * Parser for jTDS SQL Server JDBC URLs.
 *
 * <p>Sample URLs:
 *
 * <ul>
 *   <li>jtds:sqlserver://host:1433/db
 *   <li>jtds:sqlserver://host/db;instance=SQLEXPRESS
 *   <li>jtds:sqlserver://host;databaseName=db
 * </ul>
 *
 * <p>See http://jtds.sourceforge.net/faq.html#urlFormat
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
@SuppressWarnings("deprecation") // supporting old semconv until 3.0
public final class JtdsUrlParser implements JdbcUrlParser {

  public static final JtdsUrlParser INSTANCE = new JtdsUrlParser();

  // copied from DbAttributes.DbSystemNameValues
  private static final String MICROSOFT_SQL_SERVER = "microsoft.sql_server";
  // copied from DbIncubatingAttributes.DbSystemIncubatingValues
  private static final String MSSQL = "mssql";

  private static final String DEFAULT_HOST = "localhost";
  private static final int DEFAULT_PORT = 1433;

  private JtdsUrlParser() {}

  @Override
  public void parse(String jdbcUrl, ParseContext ctx) {
    ctx.system(MICROSOFT_SQL_SERVER);
    ctx.oldSemconvSystem(MSSQL);
    ctx.host(DEFAULT_HOST);
    ctx.port(DEFAULT_PORT);

    ctx.subtype("sqlserver");

    // Use ParseContext.parseUrl() to handle URL structure parsing (user, host, port, path)
    // Note: parseUrl() sets URL path to ctx.name, but for jTDS the path is the database
    ctx.parseUrl(jdbcUrl);

    // Handle jTDS/SQL Server-specific parameters
    // For jTDS, URL path (already in ctx.name from parseUrl) represents database name
    // Extract instance and database parameters
    String[] split = jdbcUrl.split(";", 2);
    String instanceName = null;
    if (split.length > 1) {
      Map<String, String> urlParams = splitQuery(split[1], ";");

      // Extract instance name from parameters
      if (urlParams.containsKey("instance")) {
        instanceName = urlParams.get("instance");
      }

      // If no path, use databasename param as fallback for database
      if (ctx.databaseName() == null && urlParams.containsKey("databasename")) {
        ctx.databaseName(urlParams.get("databasename"));
      }
    }

    // Set namespace with instance formatting (mirrors MssqlUrlParser behavior)
    if (instanceName != null && !instanceName.isEmpty()) {
      // Preserve old behavior: dbName is the instance name (not the database name)
      ctx.dbName(instanceName);
      if (ctx.databaseName() != null && !ctx.databaseName().isEmpty()) {
        ctx.namespace(instanceName + "|" + ctx.databaseName());
      } else {
        ctx.namespace(instanceName);
      }
    }

    // DataSource properties applied last — SQL Server driver gives DataSource precedence over URL
    ctx.applyDataSourceProperties();
  }
}
