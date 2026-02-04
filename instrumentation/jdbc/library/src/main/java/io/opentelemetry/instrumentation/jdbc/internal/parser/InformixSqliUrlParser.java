/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal.parser;

/**
 * Parser for IBM Informix SQLI JDBC URLs.
 *
 * <p>Sample URLs:
 *
 * <ul>
 *   <li>informix-sqli://host:9088/db:INFORMIXSERVER=server
 *   <li>informix-sqli://host/db
 * </ul>
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
@SuppressWarnings("deprecation") // supporting old semconv until 3.0
public final class InformixSqliUrlParser implements JdbcUrlParser {

  // copied from DbIncubatingAttributes.DbSystemNameIncubatingValues
  private static final String IBM_INFORMIX = "ibm.informix";
  private static final String OLD_SYSTEM = "informix-sqli";

  private static final int DEFAULT_PORT = 9088;

  public static final InformixSqliUrlParser INSTANCE = new InformixSqliUrlParser();

  private InformixSqliUrlParser() {}

  @Override
  public void parse(String jdbcUrl, ParseContext ctx) {
    ctx.system(IBM_INFORMIX);
    ctx.oldSemconvSystem(OLD_SYSTEM);
    ctx.port(DEFAULT_PORT);

    ctx.applyDataSourceProperties();

    // Parse URL for host/port (parseUrl also extracts path as databaseName, but Informix uses
    // colon-separated params in the path, so we override databaseName below)
    ctx.parseUrl(jdbcUrl);

    // Override database name: Informix paths use format /db:INFORMIXSERVER=server
    // Strip the colon-delimited params that parseUrl would have included
    int hostIndex = jdbcUrl.indexOf("://");
    if (hostIndex != -1) {
      int dbNameStartIndex = jdbcUrl.indexOf('/', hostIndex + 3);
      if (dbNameStartIndex != -1) {
        int dbNameEndIndex = jdbcUrl.indexOf(':', dbNameStartIndex);
        if (dbNameEndIndex == -1) {
          dbNameEndIndex = jdbcUrl.length();
        }
        String databaseName = jdbcUrl.substring(dbNameStartIndex + 1, dbNameEndIndex);
        if (!databaseName.isEmpty()) {
          ctx.databaseName(databaseName);
        }
      }
    }
  }
}
