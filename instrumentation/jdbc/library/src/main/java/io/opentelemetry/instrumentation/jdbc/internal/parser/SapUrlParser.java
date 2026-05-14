/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal.parser;

/**
 * Parser for SAP HANA JDBC URLs.
 *
 * <p>Sample URLs:
 *
 * <ul>
 *   <li>sap://host:30015
 *   <li>sap://host:30015/db?user=system
 * </ul>
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
@SuppressWarnings("deprecation") // supporting old semconv until 3.0
public final class SapUrlParser implements JdbcUrlParser {

  // copied from DbIncubatingAttributes.DbSystemNameIncubatingValues
  private static final String SAP_HANA = "sap.hana";
  // copied from DbIncubatingAttributes.DbSystemIncubatingValues
  private static final String HANADB = "hanadb";

  private static final String DEFAULT_HOST = "localhost";

  public static final SapUrlParser INSTANCE = new SapUrlParser();

  private SapUrlParser() {}

  @Override
  public void parse(String jdbcUrl, ParseContext ctx) {
    ctx.system(SAP_HANA);
    ctx.oldSemconvSystem(HANADB);
    ctx.host(DEFAULT_HOST);

    // SAP HANA driver doesn't support serverName/portNumber/databaseName DataSource properties
    ctx.applyUserProperty();

    GenericUrlParser.INSTANCE.parse(jdbcUrl, ctx);
  }
}
