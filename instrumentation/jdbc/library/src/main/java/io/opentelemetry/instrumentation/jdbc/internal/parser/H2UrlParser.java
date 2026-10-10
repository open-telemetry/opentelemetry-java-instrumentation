/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal.parser;

/**
 * Parser for H2 Database JDBC URLs.
 *
 * <p>Sample URLs:
 *
 * <ul>
 *   <li>h2:mem:testdb (in-memory)
 *   <li>h2:file:/path/to/db (file-based)
 *   <li>h2:zip:/path/to/db.zip!/db (zip archive)
 *   <li>h2:tcp://host:8082/db (network)
 *   <li>h2:ssl://host:8082/db (secure network)
 * </ul>
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class H2UrlParser implements JdbcUrlParser {

  // copied from DbIncubatingAttributes.DbSystemNameIncubatingValues
  private static final String H2DATABASE = "h2database";

  private static final int DEFAULT_PORT = 8082;
  private static final String[] LOCAL_MODES = {"mem", "file", "zip"};
  private static final String[] NETWORK_MODES = {"tcp", "ssl"};

  public static final H2UrlParser INSTANCE = new H2UrlParser();

  private H2UrlParser() {}

  @Override
  public void parse(String jdbcUrl, ParseContext ctx) {
    ctx.system(H2DATABASE);

    ctx.applyUserProperty();

    String h2Url = jdbcUrl.substring("h2:".length());

    for (String mode : LOCAL_MODES) {
      String prefix = mode + ":";
      if (h2Url.startsWith(prefix)) {
        parseLocalMode(h2Url.substring(prefix.length()), ctx);
        return;
      }
    }

    for (String mode : NETWORK_MODES) {
      if (h2Url.startsWith(mode + ":")) {
        parseNetworkMode(jdbcUrl, ctx);
        return;
      }
    }

    // Default to file
    parseLocalMode(h2Url, ctx);
  }

  private static void parseLocalMode(String remainder, ParseContext ctx) {
    // Local modes have no network host/port — clear any values set by DataSource properties
    ctx.host(null);
    ctx.port(null);

    int propLoc = remainder.indexOf(";");
    String databaseName = propLoc >= 0 ? remainder.substring(0, propLoc) : remainder;

    if (!databaseName.isEmpty()) {
      ctx.databaseName(databaseName);
    }
  }

  private static void parseNetworkMode(String jdbcUrl, ParseContext ctx) {
    ctx.defaultPort(DEFAULT_PORT);
    ctx.parseUrl(jdbcUrl);
  }
}
