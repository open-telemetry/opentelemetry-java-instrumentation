/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal.parser;

/**
 * Parser for Alibaba Lindorm JDBC URLs.
 *
 * <p>Sample URLs:
 *
 * <ul>
 *   <li>jdbc:lindorm:table:url=http//server_name:30060/test
 *   <li>jdbc:lindorm:tsdb:url=http://server_name:8242/test
 *   <li>jdbc:lindorm:search:url=http://server_name:30070/test
 * </ul>
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class LindormUrlParser implements JdbcUrlParser {

  private static final String SYSTEM = "lindorm";

  public static final LindormUrlParser INSTANCE = new LindormUrlParser();

  private LindormUrlParser() {}

  @Override
  public void parse(String jdbcUrl, ParseContext ctx) {
    ctx.system(SYSTEM);

    ctx.applyUserProperty();

    String lindormUrl = jdbcUrl.substring("lindorm:".length());

    int urlIndex = lindormUrl.indexOf(":url=");
    if (urlIndex < 0) {
      return;
    }

    // Extract subtype (table, tsdb, search) before :url=
    ctx.subtype(lindormUrl.substring(0, urlIndex));

    String realUrl = lindormUrl.substring(urlIndex + 5);
    GenericUrlParser.INSTANCE.parse(realUrl, ctx);
  }
}
