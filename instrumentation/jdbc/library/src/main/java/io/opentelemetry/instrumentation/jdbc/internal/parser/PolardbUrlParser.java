/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal.parser;

/**
 * Parser for Alibaba PolarDB JDBC URLs.
 *
 * <p>Sample URL: jdbc:polardb://server_name:1901/dbname
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class PolardbUrlParser implements JdbcUrlParser {

  private static final String SYSTEM = "polardb";
  private static final int DEFAULT_PORT = 1521;

  public static final PolardbUrlParser INSTANCE = new PolardbUrlParser();

  private PolardbUrlParser() {}

  @Override
  public void parse(String jdbcUrl, ParseContext ctx) {
    ctx.system(SYSTEM);
    ctx.port(DEFAULT_PORT);

    ctx.applyUserProperty();

    GenericUrlParser.INSTANCE.parse(jdbcUrl, ctx);
  }
}
