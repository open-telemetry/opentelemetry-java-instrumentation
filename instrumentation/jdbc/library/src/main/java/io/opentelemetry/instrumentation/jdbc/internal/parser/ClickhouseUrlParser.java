/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal.parser;

/**
 * Parser for ClickHouse JDBC URLs.
 *
 * <p>Sample URLs:
 *
 * <ul>
 *   <li>clickhouse:http://host:8123/db
 *   <li>clickhouse:https://host:8443/db
 *   <li>clickhouse://host:9000/db
 * </ul>
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class ClickhouseUrlParser implements JdbcUrlParser {

  private static final String SYSTEM = "clickhouse";

  public static final ClickhouseUrlParser INSTANCE = new ClickhouseUrlParser();

  private ClickhouseUrlParser() {}

  @Override
  public void parse(String jdbcUrl, ParseContext ctx) {
    ctx.system(SYSTEM);

    ctx.applyUserProperty();

    String clickhouseUrl = jdbcUrl.substring("clickhouse:".length());

    // Extract protocol (http or https) as subtype from URLs like "http://..." or "https://..."
    int protoLoc = clickhouseUrl.indexOf("://");
    if (protoLoc > 0) {
      ctx.subtype(clickhouseUrl.substring(0, protoLoc));
    }

    GenericUrlParser.INSTANCE.parse(clickhouseUrl, ctx);
  }
}
