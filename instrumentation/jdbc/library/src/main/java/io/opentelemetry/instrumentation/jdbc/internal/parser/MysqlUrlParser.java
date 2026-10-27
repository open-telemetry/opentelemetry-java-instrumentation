/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal.parser;

import static io.opentelemetry.instrumentation.jdbc.internal.parser.UrlParsingUtils.extractAuthority;
import static io.opentelemetry.instrumentation.jdbc.internal.parser.UrlParsingUtils.extractAuthorityWithQueryAt;
import static io.opentelemetry.instrumentation.jdbc.internal.parser.UrlParsingUtils.extractSubtype;
import static io.opentelemetry.instrumentation.jdbc.internal.parser.UrlParsingUtils.hasMultipleTargets;
import static io.opentelemetry.instrumentation.jdbc.internal.parser.UrlParsingUtils.isValidPort;
import static io.opentelemetry.instrumentation.jdbc.internal.parser.UrlParsingUtils.parsePort;
import static io.opentelemetry.instrumentation.jdbc.internal.parser.UrlParsingUtils.parseServerTargetGroup;
import static io.opentelemetry.instrumentation.jdbc.internal.parser.UrlParsingUtils.stripIpv6Brackets;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTarget;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTargetBuilder;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
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
 *   <li>mysql://address=(host=host1)(port=3306)/db
 *   <li>mysql://[::1]:3306/db (IPv6)
 * </ul>
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
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
    ctx.defaultHost(DEFAULT_HOST);
    ctx.defaultPort(DEFAULT_PORT);

    ctx.applyUserProperty();

    if (system.equals(MYSQL)) {
      // MariaDB Connector/J doesn't support the key-value form
      jdbcUrl = rewriteKeyValueHosts(jdbcUrl);
    }

    // Parse URL (overwrites defaults and props)
    String subtype = extractSubtype(jdbcUrl);
    int protoLoc = jdbcUrl.indexOf("://");

    if (subtype != null) {
      // Has subprotocol (e.g., mysql:aurora://...)
      parseMariaSubProtocol(jdbcUrl.substring(protoLoc + 3), ctx);
    } else if (protoLoc > 0) {
      // Standard URL format - delegate to GenericUrlParser
      GenericUrlParser.INSTANCE.parse(jdbcUrl, ctx, DEFAULT_PORT);
      if (jdbcUrl.startsWith("address=", protoLoc + 3)) {
        // java.net.URI finds no host in the address=(host=...)(port=...) form
        parseMariaAddress(jdbcUrl.substring(protoLoc + 3), ctx);
      }
    } else {
      // Non-standard format: type/host:port/db?params
      parseNonStandardUrl(jdbcUrl, ctx);
    }
  }

  // Connector/J also accepts hosts in the key-value form, e.g. (host=h1,port=3306,key1=value1).
  // Rewrite them to the equivalent address=(host=h1)(port=3306) form that is handled below.
  private static String rewriteKeyValueHosts(String jdbcUrl) {
    int protoLoc = jdbcUrl.indexOf("://");
    if (protoLoc < 0) {
      return jdbcUrl;
    }
    int hostsStart = protoLoc + 3;
    int hostsEnd = indexOf(jdbcUrl, hostsStart, '/', '?', '#');
    if (hostsEnd == -1) {
      hostsEnd = jdbcUrl.length();
    }
    int firstParenthesis = jdbcUrl.indexOf('(', hostsStart);
    if (firstParenthesis < 0 || firstParenthesis >= hostsEnd) {
      return jdbcUrl;
    }
    StringBuilder hosts = null;
    int entryStart = hostsStart;
    int depth = 0;
    for (int i = hostsStart; i <= hostsEnd; i++) {
      char c = i < hostsEnd ? jdbcUrl.charAt(i) : ',';
      if (c == '(') {
        depth++;
      } else if (c == ')') {
        depth--;
      } else if (c == ',' && depth <= 0) {
        String address = toAddressForm(jdbcUrl, entryStart, i);
        if (address != null && hosts == null) {
          hosts = new StringBuilder().append(jdbcUrl, hostsStart, entryStart);
        }
        if (hosts != null) {
          if (address != null) {
            hosts.append(address);
          } else {
            hosts.append(jdbcUrl, entryStart, i);
          }
          if (i < hostsEnd) {
            hosts.append(',');
          }
        }
        entryStart = i + 1;
      }
    }
    if (depth != 0 || hosts == null) {
      // leave unbalanced parentheses to the parsing below, which rejects them
      return jdbcUrl;
    }
    return jdbcUrl.substring(0, hostsStart) + hosts + jdbcUrl.substring(hostsEnd);
  }

  // Returns null when the entry is not a valid key-value host.
  @Nullable
  private static String toAddressForm(String jdbcUrl, int start, int end) {
    int first = start;
    while (first < end && Character.isWhitespace(jdbcUrl.charAt(first))) {
      first++;
    }
    if (first == end || jdbcUrl.charAt(first) != '(') {
      return null;
    }
    String entry = jdbcUrl.substring(first, end).trim();
    if (!entry.endsWith(")")) {
      return null;
    }
    String keyValues = entry.substring(1, entry.length() - 1);
    // Connector/J reads an unencoded @ as the end of the user info
    if (keyValues.indexOf('(') >= 0 || keyValues.indexOf(')') >= 0 || keyValues.indexOf('@') >= 0) {
      return null;
    }
    String host = null;
    String port = null;
    boolean hasHost = false;
    boolean hasPort = false;
    for (String keyValue : keyValues.split(",", -1)) {
      int equals = keyValue.indexOf('=');
      String rawKey = equals < 0 ? keyValue : keyValue.substring(0, equals);
      if (!isValidKey(rawKey)) {
        return null;
      }
      String key = decode(rawKey.trim());
      // Connector/J reads a key without a value as null, e.g. after an unencoded comma in a
      // password
      String value = equals < 0 ? null : decode(keyValue.substring(equals + 1).trim());
      if (key == null || (equals >= 0 && value == null)) {
        return null;
      }
      if (key.isEmpty()) {
        // Connector/J skips empty entries and rejects a value without a key
        if (value != null && !value.isEmpty()) {
          return null;
        }
        continue;
      }
      // the URL has been lowercased, so a repeated key may have differed only in case, for which
      // Connector/J doesn't define the value it uses
      if (key.equalsIgnoreCase("host")) {
        if (hasHost) {
          return null;
        }
        hasHost = true;
        host = value;
      } else if (key.equalsIgnoreCase("port")) {
        if (hasPort) {
          return null;
        }
        hasPort = true;
        port = value == null || value.isEmpty() ? null : value;
      }
    }
    // the decoded host must not contain characters that would break the address block
    // a decoded space around the host would make the rewritten URL an invalid URI
    if (host == null
        || !host.equals(host.trim())
        || !DbServerTargetBuilder.isValidHost(host)
        || (port != null && !isValidPort(port))) {
      return null;
    }
    // brackets and an IPv6 zone id would make the rewritten URL an invalid URI
    host = stripIpv6Brackets(host);
    if (host.indexOf('%') >= 0) {
      return null;
    }
    String address = "address=(host=" + host + ")";
    return port == null ? address : address + "(port=" + port + ")";
  }

  // the characters Connector/J accepts in a key-value host key
  private static boolean isValidKey(String key) {
    for (int i = 0; i < key.length(); i++) {
      char c = key.charAt(i);
      if ((c < 'a' || c > 'z')
          && (c < 'A' || c > 'Z')
          && (c < '0' || c > '9')
          && c != '_'
          && c != '.'
          && c != '-'
          && c != '%'
          && c != ' '
          && (c < '\t' || c > '\r')) {
        return false;
      }
    }
    return true;
  }

  // Connector/J percent-decodes key-value host properties
  @Nullable
  private static String decode(String value) {
    try {
      return URLDecoder.decode(value, "UTF-8");
    } catch (UnsupportedEncodingException | IllegalArgumentException ignored) {
      return null;
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

    if (jdbcUrl.startsWith("(")) {
      // a key-value host that rewriteKeyValueHosts left as it is, not a host name
      ctx.applyCommonParams(jdbcUrl, "?", "&");
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

  private static boolean applyHostGroup(String jdbcUrl, ParseContext ctx) {
    String authority = extractAuthority("mariadb://" + jdbcUrl);
    if (authority == null) {
      authority = extractAuthorityWithQueryAt(jdbcUrl);
      if (authority == null) {
        return false;
      }
    }
    DbServerTarget target = parseServerTargetGroup(authority, DEFAULT_PORT);
    if (target != null || hasMultipleTargets(jdbcUrl)) {
      ctx.resolveConfiguredServerTarget(target);
    }
    return true;
  }

  private static final Pattern HOST_PATTERN =
      Pattern.compile("\\(\\s*host\\s*=\\s*([^ )]+)\\s*\\)");
  private static final Pattern PORT_PATTERN =
      Pattern.compile("\\(\\s*port\\s*=\\s*([\\d]+)\\s*\\)");

  private static void parseMariaAddress(String jdbcUrl, ParseContext ctx) {
    // only look at the address blocks, not at the database name or the query string
    int authorityEnd = indexOf(jdbcUrl, 0, '/', '?');
    String authority = authorityEnd != -1 ? jdbcUrl.substring(0, authorityEnd) : jdbcUrl;
    int addressEnd = authority.indexOf(",address=");
    String addressUrl = addressEnd > 0 ? authority.substring(0, addressEnd) : authority;

    Matcher hostMatcher = HOST_PATTERN.matcher(addressUrl);
    if (hostMatcher.find()) {
      ctx.host(hostMatcher.group(1));
    }

    Matcher portMatcher = PORT_PATTERN.matcher(addressUrl);
    if (portMatcher.find()) {
      ctx.port(Integer.parseInt(portMatcher.group(1)));
    }
  }
}
