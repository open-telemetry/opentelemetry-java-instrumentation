/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal.parser;

import static io.opentelemetry.instrumentation.jdbc.internal.parser.UrlParsingUtils.extractAuthority;
import static io.opentelemetry.instrumentation.jdbc.internal.parser.UrlParsingUtils.sanitizeHostList;
import static java.util.logging.Level.FINE;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.logging.Logger;

/**
 * Parses standard URL-like JDBC connection strings using Java's URI parser.
 *
 * <p>Used by database-specific parsers to handle standard URL formats like: {@code
 * type://host:port/db?user=username}
 *
 * <p>An authority that lists more than one host, e.g. {@code type://h1:5432,h2:5432/db}, is not a
 * server-based URI. Its host list is kept as the configured group target; every other value comes
 * from the URI parse, which reports such an authority as registry-based and therefore leaves the
 * host and the port alone.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
@SuppressWarnings("deprecation") // supporting old semconv until 3.0
public final class GenericUrlParser implements JdbcUrlParser {

  // copied from DbIncubatingAttributes.DbSystemNameIncubatingValues
  private static final String OTHER_SQL = "other_sql";

  private static final Logger logger = Logger.getLogger(GenericUrlParser.class.getName());

  public static final GenericUrlParser INSTANCE = new GenericUrlParser();

  private GenericUrlParser() {}

  @Override
  public void parse(String jdbcUrl, ParseContext ctx) {
    if (ctx.system() == null) {
      ctx.system(OTHER_SQL);
    }

    if (!applyHostGroup(jdbcUrl, ctx)) {
      return;
    }

    URI uri;
    try {
      uri = new URI(jdbcUrl);
    } catch (URISyntaxException e) {
      logger.log(FINE, e.getMessage(), e);
      return;
    }

    // 1. User from URI userInfo
    String uriUser = uri.getUserInfo();
    if (uriUser != null) {
      int colonIndex = uriUser.indexOf(':');
      if (colonIndex != -1) {
        uriUser = uriUser.substring(0, colonIndex);
      }
      ctx.user(uriUser);
    }

    // 2. Host and port from URI
    if (uri.getHost() != null) {
      ctx.host(uri.getHost());
    }
    if (uri.getPort() > 0) {
      ctx.port(uri.getPort());
    }

    // 3. Database from path
    String databaseName = uri.getPath();
    if (databaseName == null) {
      databaseName = "";
    }
    if (databaseName.startsWith("/")) {
      databaseName = databaseName.substring(1);
    }
    if (!databaseName.isEmpty()) {
      ctx.databaseName(databaseName);
    }

    // 4. Query params (highest precedence)
    // URL is lowercased by JdbcConnectionUrlParser, so check lowercase param names
    ctx.applyCommonParams(jdbcUrl, "?", "&");
  }

  /**
   * Keep the whole host list of an authority that routes to more than one host, e.g. {@code
   * postgresql://h1:5432,h2:5432/db}, as the configured group target.
   */
  private static boolean applyHostGroup(String jdbcUrl, ParseContext ctx) {
    String authority = extractAuthority(jdbcUrl);
    if (authority == null) {
      return jdbcUrl.indexOf("://") < 0;
    }
    String hostList = sanitizeHostList(authority);
    if (hostList != null) {
      ctx.serverAddressGroup(hostList);
    }
    return true;
  }
}
