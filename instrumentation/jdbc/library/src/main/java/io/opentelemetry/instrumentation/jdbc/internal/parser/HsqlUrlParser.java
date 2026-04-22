/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal.parser;

import static io.opentelemetry.instrumentation.jdbc.internal.parser.UrlParsingUtils.indexOfAny;

import java.util.HashMap;
import java.util.Map;

/**
 * Parser for HyperSQL (HSQLDB) JDBC URLs.
 *
 * <p>Sample URLs:
 *
 * <ul>
 *   <li>hsqldb:mem:testdb (in-memory)
 *   <li>hsqldb:file:/path/to/db (file-based)
 *   <li>hsqldb:res:/path/to/db (resource)
 *   <li>hsqldb:hsql://host:9001/db (network)
 *   <li>hsqldb:hsqls://host:9001/db (secure network)
 *   <li>hsqldb:http://host:80/db (HTTP)
 *   <li>hsqldb:https://host:443/db (HTTPS)
 * </ul>
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
@SuppressWarnings("deprecation") // supporting old semconv until 3.0
public final class HsqlUrlParser implements JdbcUrlParser {

  // copied from DbIncubatingAttributes.DbSystemNameIncubatingValues
  private static final String HSQLDB = "hsqldb";

  private static final String DEFAULT_USER = "SA";
  private static final int DEFAULT_PORT = 9001;
  private static final String[] LOCAL_MODES = {"mem", "file", "res"};
  private static final Map<String, Integer> NETWORK_MODES = buildNetworkModes();

  private static Map<String, Integer> buildNetworkModes() {
    Map<String, Integer> modes = new HashMap<>(4);
    modes.put("hsql", DEFAULT_PORT);
    modes.put("hsqls", DEFAULT_PORT);
    modes.put("http", 80);
    modes.put("https", 443);
    return modes;
  }

  public static final HsqlUrlParser INSTANCE = new HsqlUrlParser();

  private HsqlUrlParser() {}

  @Override
  public void parse(String jdbcUrl, ParseContext ctx) {
    ctx.system(HSQLDB);
    ctx.user(DEFAULT_USER);

    ctx.applyUserProperty();

    String hsqlUrl = jdbcUrl.substring("hsqldb:".length());

    // Strip parameters (semicolon or query string)
    int paramIndex = indexOfAny(hsqlUrl, ';', '?');
    if (paramIndex >= 0) {
      hsqlUrl = hsqlUrl.substring(0, paramIndex);
    }

    for (String mode : LOCAL_MODES) {
      String prefix = mode + ":";
      if (hsqlUrl.startsWith(prefix)) {
        parseLocalMode(hsqlUrl.substring(prefix.length()), mode, ctx);
        return;
      }
    }

    for (Map.Entry<String, Integer> entry : NETWORK_MODES.entrySet()) {
      if (hsqlUrl.startsWith(entry.getKey() + ":")) {
        parseNetworkMode(entry.getKey(), jdbcUrl, entry.getValue(), ctx);
        return;
      }
    }

    // Default to mem
    parseLocalMode(hsqlUrl, "mem", ctx);
  }

  private static void parseLocalMode(String databaseName, String subtype, ParseContext ctx) {
    // Local modes have no network host/port — clear any values set by DataSource properties
    ctx.host(null);
    ctx.port(null);

    if (!databaseName.isEmpty()) {
      ctx.databaseName(databaseName);
    }
    ctx.subtype(subtype);
  }

  private static void parseNetworkMode(
      String subtype, String jdbcUrl, int defaultPort, ParseContext ctx) {
    ctx.port(defaultPort);
    ctx.subtype(subtype);
    ctx.parseUrl(jdbcUrl);
  }
}
