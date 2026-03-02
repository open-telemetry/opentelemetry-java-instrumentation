/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal.parser;

/**
 * Parser for IBM DB2 and AS400 JDBC URLs.
 *
 * <p>Sample URLs:
 *
 * <ul>
 *   <li>db2://host:50000/db
 *   <li>as400://host:50000/db
 *   <li>db2://host:50000/db:user=dbuser;password=pass;
 * </ul>
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
@SuppressWarnings("deprecation") // supporting old semconv until 3.0
public final class Db2UrlParser implements JdbcUrlParser {

  // copied from DbIncubatingAttributes.DbSystemNameIncubatingValues
  private static final String IBM_DB2 = "ibm.db2";
  // copied from DbIncubatingAttributes.DbSystemIncubatingValues
  private static final String DB2 = "db2";

  private static final int DEFAULT_PORT = 50000;

  public static final Db2UrlParser INSTANCE = new Db2UrlParser();

  private Db2UrlParser() {}

  @Override
  public void parse(String jdbcUrl, ParseContext ctx) {
    ctx.system(IBM_DB2);
    ctx.oldSemconvSystem(DB2);
    ctx.port(DEFAULT_PORT);

    ctx.applyDataSourceProperties();

    // DB2/AS400 uses colon to separate URL from params, semicolons within params
    if (jdbcUrl.contains("=")) {
      int paramLoc = jdbcUrl.lastIndexOf(":");
      if (paramLoc >= 0) {
        // Prepend semicolon delimiter so extractParams can find and parse params
        ctx.applyCommonParams(";" + jdbcUrl.substring(paramLoc + 1), ";", ";");
        ctx.parseUrl(jdbcUrl.substring(0, paramLoc));
      } else {
        ctx.parseUrl(jdbcUrl);
      }
    } else {
      ctx.parseUrl(jdbcUrl);
    }
  }
}
