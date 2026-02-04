/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal.parser;

import static io.opentelemetry.instrumentation.jdbc.internal.parser.UrlParsingUtils.extractSubtype;
import static io.opentelemetry.instrumentation.jdbc.internal.parser.UrlParsingUtils.parsePort;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for MySQL and MariaDB JDBC URLs.
 *
 * <p>Sample URLs:
 *
 * <ul>
 *   <li>mysql://host:3306/db
 *   <li>mysql://host/db?user=root
 *   <li>mysql:aurora://host:3306/db
 *   <li>mysql:host:3306/db (non-standard format)
 *   <li>mariadb:replication://host1,host2/db
 *   <li>mariadb:sequential:address=(host=host1)(port=3306)(user=root)/db
 *   <li>mysql://[::1]:3306/db (IPv6)
 * </ul>
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
@SuppressWarnings("deprecation") // supporting old semconv until 3.0
public final class MysqlUrlParser implements JdbcUrlParser {

  // copied from DbAttributes.DbSystemNameValues
  private static final String MYSQL = "mysql";
  // copied from DbAttributes.DbSystemNameValues
  private static final String MARIADB = "mariadb";
  // copied from DbIncubatingAttributes.DbSystemNameIncubatingValues
  private static final String OTHER_SQL = "other_sql";

  private static final Map<String, String> TYPE_TO_SYSTEM = buildTypeToSystem();

  private static Map<String, String> buildTypeToSystem() {
    Map<String, String> map = new HashMap<>(2);
    map.put("mysql", MYSQL);
    map.put("mariadb", MARIADB);
    return map;
  }

  private static final String DEFAULT_HOST = "localhost";
  private static final int DEFAULT_PORT = 3306;

  public static final MysqlUrlParser INSTANCE = new MysqlUrlParser();

  private MysqlUrlParser() {}

  @Override
  public void parse(String jdbcUrl, ParseContext ctx) {
    String system = TYPE_TO_SYSTEM.get(ctx.type());
    if (system == null) {
      // not possible: JdbcConnectionUrlParser only maps "mysql" and "mariadb" to this parser
      system = OTHER_SQL;
    }
    ctx.system(system);
    ctx.host(DEFAULT_HOST);
    ctx.port(DEFAULT_PORT);

    ctx.applyUserProperty();

    // Parse URL (overwrites defaults and props)
    String subtype = extractSubtype(jdbcUrl);
    int protoLoc = jdbcUrl.indexOf("://");

    if (subtype != null) {
      // Has subprotocol (e.g., mysql:aurora://...)
      ctx.subtype(subtype);
      parseMariaSubProtocol(jdbcUrl.substring(protoLoc + 3), ctx);
    } else if (protoLoc > 0) {
      // Standard URL format - delegate to GenericUrlParser
      GenericUrlParser.INSTANCE.parse(jdbcUrl, ctx);
    } else {
      // Non-standard format: type/host:port/db?params
      parseNonStandardUrl(jdbcUrl, ctx);
    }
  }

  private static void parseNonStandardUrl(String jdbcUrl, ParseContext ctx) {
    int typeEndLoc = jdbcUrl.indexOf(':');
    int portLoc = jdbcUrl.indexOf(":", typeEndLoc + 1);
    int dbLoc = jdbcUrl.indexOf("/", typeEndLoc);
    int paramLoc = jdbcUrl.indexOf("?", dbLoc);

    // Extract database name
    String databaseName;
    if (paramLoc > 0) {
      databaseName = jdbcUrl.substring(dbLoc + 1, paramLoc);
    } else {
      databaseName = jdbcUrl.substring(dbLoc + 1);
    }
    ctx.databaseName(databaseName);

    // Host and port from URL
    int hostEndLoc;
    if (portLoc > 0) {
      hostEndLoc = portLoc;
      Integer parsedPort = parsePort(jdbcUrl.substring(portLoc + 1, dbLoc));
      if (parsedPort != null) {
        ctx.port(parsedPort);
      }
    } else {
      hostEndLoc = dbLoc;
    }
    ctx.host(jdbcUrl.substring(typeEndLoc + 1, hostEndLoc));

    // Apply query params (highest precedence)
    ctx.applyCommonParams(jdbcUrl, "?", "&");
  }

  private static void parseMariaSubProtocol(String jdbcUrl, ParseContext ctx) {
    int hostEndLoc;
    int clusterSepLoc = jdbcUrl.indexOf(",");
    int ipv6End = jdbcUrl.startsWith("[") ? jdbcUrl.indexOf("]") : -1;
    int portLoc = jdbcUrl.indexOf(":", Math.max(0, ipv6End));
    portLoc = clusterSepLoc != -1 && clusterSepLoc < portLoc ? -1 : portLoc;
    int dbLoc = jdbcUrl.indexOf("/", Math.max(portLoc, clusterSepLoc));

    int paramLoc = dbLoc != -1 ? jdbcUrl.indexOf("?", dbLoc) : -1;

    if (paramLoc > 0) {
      ctx.databaseName(jdbcUrl.substring(dbLoc + 1, paramLoc));
    } else if (dbLoc != -1) {
      ctx.databaseName(jdbcUrl.substring(dbLoc + 1));
    }

    if (jdbcUrl.startsWith("address=")) {
      // Apply query params first so address fields can override them
      ctx.applyCommonParams(jdbcUrl, "?", "&");
      parseMariaAddress(jdbcUrl, ctx);
      return;
    }

    int effectiveDbLoc = dbLoc != -1 ? dbLoc : jdbcUrl.length();
    if (portLoc > 0) {
      hostEndLoc = portLoc;
      int portEndLoc = clusterSepLoc > 0 ? clusterSepLoc : effectiveDbLoc;
      Integer parsedPort = parsePort(jdbcUrl.substring(portLoc + 1, portEndLoc));
      if (parsedPort != null) {
        ctx.port(parsedPort);
      }
    } else {
      hostEndLoc = clusterSepLoc > 0 ? clusterSepLoc : effectiveDbLoc;
    }

    if (ipv6End > 0) {
      ctx.host(jdbcUrl.substring(1, ipv6End));
    } else {
      ctx.host(jdbcUrl.substring(0, hostEndLoc));
    }

    // Apply query params (highest precedence)
    ctx.applyCommonParams(jdbcUrl, "?", "&");
  }

  private static final Pattern HOST_PATTERN =
      Pattern.compile("\\(\\s*host\\s*=\\s*([^ )]+)\\s*\\)");
  private static final Pattern PORT_PATTERN =
      Pattern.compile("\\(\\s*port\\s*=\\s*([\\d]+)\\s*\\)");
  private static final Pattern USER_PATTERN =
      Pattern.compile("\\(\\s*user\\s*=\\s*([^ )]+)\\s*\\)");

  private static void parseMariaAddress(String jdbcUrl, ParseContext ctx) {
    int addressEnd = jdbcUrl.indexOf(",address=");
    String addressUrl = addressEnd > 0 ? jdbcUrl.substring(0, addressEnd) : jdbcUrl;

    Matcher hostMatcher = HOST_PATTERN.matcher(addressUrl);
    if (hostMatcher.find()) {
      ctx.host(hostMatcher.group(1));
    }

    Matcher portMatcher = PORT_PATTERN.matcher(addressUrl);
    if (portMatcher.find()) {
      ctx.port(Integer.parseInt(portMatcher.group(1)));
    }

    Matcher userMatcher = USER_PATTERN.matcher(addressUrl);
    if (userMatcher.find()) {
      ctx.user(userMatcher.group(1));
    }
  }
}
