/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal.parser;

import static io.opentelemetry.instrumentation.jdbc.internal.parser.UrlParsingUtils.splitQuery;

import java.util.Map;

/**
 * Parses PostgreSQL JDBC connection strings.
 *
 * <p>Sample URLs:
 *
 * <ul>
 *   <li>postgresql://host:5432/db
 *   <li>postgresql://host/db?user=postgres
 *   <li>postgresql://user@host/db
 *   <li>pgsql://host:5432/db
 *   <li>postgresql://host/db?currentSchema=myschema
 * </ul>
 *
 * <p>PostgreSQL defaults to localhost:5432 when host/port are not specified.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
@SuppressWarnings("deprecation") // supporting old semconv until 3.0
public final class PostgresqlUrlParser implements JdbcUrlParser {

  // copied from DbAttributes.DbSystemNameValues
  private static final String POSTGRESQL = "postgresql";

  private static final String DEFAULT_HOST = "localhost";
  private static final int DEFAULT_PORT = 5432;

  public static final PostgresqlUrlParser INSTANCE = new PostgresqlUrlParser();

  private PostgresqlUrlParser() {}

  @Override
  public void parse(String jdbcUrl, ParseContext ctx) {
    ctx.system(POSTGRESQL);
    ctx.host(DEFAULT_HOST);
    ctx.port(DEFAULT_PORT);

    ctx.applyUserProperty();

    // Delegate to generic parser for standard URL parsing
    GenericUrlParser.INSTANCE.parse(jdbcUrl, ctx);

    // Extract schema from currentSchema URL parameter for namespace formatting
    String schema = extractCurrentSchema(jdbcUrl);
    if (schema == null && ctx.user() != null) {
      // Fall back to user as schema if no currentSchema param
      schema = ctx.user();
    }

    // Format namespace as database|schema (only when schema is available)
    String database = ctx.databaseName();
    if (database != null && schema != null) {
      ctx.namespace(database + "|" + schema);
    }
  }

  private static String extractCurrentSchema(String jdbcUrl) {
    int queryIndex = jdbcUrl.indexOf('?');
    if (queryIndex < 0) {
      return null;
    }
    Map<String, String> params = splitQuery(jdbcUrl.substring(queryIndex + 1), "&");
    return params.get("currentschema");
  }
}
