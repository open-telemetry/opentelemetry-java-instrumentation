/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal.parser;

import static io.opentelemetry.instrumentation.jdbc.internal.parser.UrlParsingUtils.extractAuthority;
import static io.opentelemetry.instrumentation.jdbc.internal.parser.UrlParsingUtils.extractSubtype;
import static io.opentelemetry.instrumentation.jdbc.internal.parser.UrlParsingUtils.parsePort;
import static io.opentelemetry.instrumentation.jdbc.internal.parser.UrlParsingUtils.sanitizeHostList;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

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

  private static int indexOf(String string, int start, char... chars) {
    for (int i = start; i < string.length(); i++) {
      char c = string.charAt(i);
      for (char match : chars) {
        if (c == match) {
          return i;
        }
      }
    }
    return -1;
  }

  /**
   * Keep the whole host list of a sub-protocol URL, e.g. {@code failover://h1:3306,h2:3306/db},
   * when it routes to more than one host.
   *
   * @param jdbcUrl the part of the URL that follows {@code ://}
   */
  private static boolean applyHostGroup(String jdbcUrl, ParseContext ctx) {
    String authority = extractAuthority("mariadb://" + jdbcUrl);
    if (authority == null) {
      authority = extractAuthorityWithQueryAt(jdbcUrl);
      if (authority == null) {
        return false;
      }
    }
    String hostList = sanitizeHostList(authority);
    if (hostList != null) {
      ctx.serverAddressGroup("//" + hostList);
    }
    return true;
  }

  @Nullable
  private static String extractAuthorityWithQueryAt(String jdbcUrl) {
    int authorityEnd = indexOf(jdbcUrl, 0, '/', '?', '#');
    if (authorityEnd < 0) {
      return null;
    }
    int queryStart = jdbcUrl.indexOf('?', authorityEnd);
    int credentialsEnd = jdbcUrl.lastIndexOf('@');
    int parameterStart =
        queryStart < 0 ? -1 : Math.max(queryStart, jdbcUrl.lastIndexOf('&', credentialsEnd)) + 1;
    int parameterEnd = jdbcUrl.indexOf('&', credentialsEnd);
    if (parameterEnd < 0) {
      parameterEnd = jdbcUrl.length();
    }
    int equals = parameterStart < 0 ? -1 : jdbcUrl.indexOf('=', parameterStart);
    if (queryStart < 0
        || credentialsEnd < queryStart
        || equals < parameterStart
        || equals > credentialsEnd) {
      return null;
    }
    String suffix = jdbcUrl.substring(credentialsEnd + 1, parameterEnd);
    if (suffix.indexOf('/') >= 0 || suffix.indexOf(',') >= 0) {
      return null;
    }
    String authority = jdbcUrl.substring(0, authorityEnd);
    return hasValidHostPorts(authority) ? authority : null;
  }

  private static boolean hasValidHostPorts(String authority) {
    for (String endpoint : authority.split(",")) {
      String value = endpoint.trim();
      int closingBracket = value.startsWith("[") ? value.indexOf(']') : -1;
      if (closingBracket >= 0) {
        String rest = value.substring(closingBracket + 1);
        if (!rest.isEmpty() && (!rest.startsWith(":") || parsePort(rest.substring(1)) == null)) {
          return false;
        }
        continue;
      }
      int colon = value.lastIndexOf(':');
      if (colon >= 0
          && value.indexOf(':') == colon
          && parsePort(value.substring(colon + 1)) == null) {
        return false;
      }
    }
    return true;
  }

  private static void parseNonStandardUrl(String jdbcUrl, ParseContext ctx) {
    int typeEndLoc = jdbcUrl.indexOf(':');
    int sectionEnd = indexOf(jdbcUrl, typeEndLoc + 1, ':', '/', '?');
    int portLoc = -1;
    if (sectionEnd != -1 && jdbcUrl.charAt(sectionEnd) == ':') {
      portLoc = sectionEnd;
      sectionEnd = indexOf(jdbcUrl, sectionEnd + 1, '/', '?');
    }
    int dbLoc = -1;
    if (sectionEnd != -1 && jdbcUrl.charAt(sectionEnd) == '/') {
      dbLoc = sectionEnd;
      sectionEnd = indexOf(jdbcUrl, sectionEnd + 1, '?');
    }
    int paramLoc = -1;
    if (dbLoc != -1 && sectionEnd != -1 && jdbcUrl.charAt(sectionEnd) == '?') {
      paramLoc = sectionEnd;
    }

    // Extract database name
    if (paramLoc > 0) {
      ctx.databaseName(jdbcUrl.substring(dbLoc + 1, paramLoc));
    } else if (dbLoc != -1) {
      ctx.databaseName(jdbcUrl.substring(dbLoc + 1));
    }

    // Host and port from URL
    int hostEndLoc;
    // without a database the host/port segment ends at the query string, if any
    int urlEndLoc = dbLoc != -1 ? dbLoc : jdbcUrl.indexOf('?');
    int effectiveDbLoc = urlEndLoc != -1 ? urlEndLoc : jdbcUrl.length();
    if (portLoc > 0) {
      hostEndLoc = portLoc;
      Integer parsedPort = parsePort(jdbcUrl.substring(portLoc + 1, effectiveDbLoc));
      if (parsedPort != null) {
        ctx.port(parsedPort);
      }
    } else {
      hostEndLoc = effectiveDbLoc;
    }
    ctx.host(jdbcUrl.substring(typeEndLoc + 1, hostEndLoc));

    // Apply query params (highest precedence)
    ctx.applyCommonParams(jdbcUrl, "?", "&");
  }

  private static void parseMariaSubProtocol(String jdbcUrl, ParseContext ctx) {
    if (!applyHostGroup(jdbcUrl, ctx)) {
      ctx.host(null);
      ctx.port(null);
      return;
    }

    int hostEndLoc;
    int ipv6End = jdbcUrl.startsWith("[") ? jdbcUrl.indexOf("]") : -1;
    int sectionEnd = indexOf(jdbcUrl, Math.max(0, ipv6End), ':', '/', '?', ',');
    int clusterSepLoc = -1;
    if (sectionEnd != -1 && jdbcUrl.charAt(sectionEnd) == ',') {
      clusterSepLoc = sectionEnd;
      // port will be skipped if cluster separator was found
      sectionEnd = indexOf(jdbcUrl, sectionEnd + 1, '/', '?');
    }
    int portLoc = -1;
    if (clusterSepLoc == -1 && sectionEnd != -1 && jdbcUrl.charAt(sectionEnd) == ':') {
      portLoc = sectionEnd;
      sectionEnd = indexOf(jdbcUrl, sectionEnd + 1, '/', '?', ',');
    }
    // when there are multiple addresses we only care about the first one
    if (portLoc != -1 && sectionEnd != -1 && jdbcUrl.charAt(sectionEnd) == ',') {
      clusterSepLoc = sectionEnd;
      sectionEnd = indexOf(jdbcUrl, sectionEnd + 1, '/', '?');
    }
    int dbLoc = -1;
    if (sectionEnd != -1 && jdbcUrl.charAt(sectionEnd) == '/') {
      dbLoc = sectionEnd;
      sectionEnd = indexOf(jdbcUrl, sectionEnd + 1, '?');
    }
    int paramLoc = -1;
    if (dbLoc != -1 && sectionEnd != -1 && jdbcUrl.charAt(sectionEnd) == '?') {
      paramLoc = sectionEnd;
    }

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

    // without a database the host/port segment ends at the query string, if any
    int urlEndLoc = dbLoc != -1 ? dbLoc : jdbcUrl.indexOf('?');
    int effectiveDbLoc = urlEndLoc != -1 ? urlEndLoc : jdbcUrl.length();
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
