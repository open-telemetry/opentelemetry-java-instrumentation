/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal.parser;

import static io.opentelemetry.instrumentation.jdbc.internal.parser.UrlParsingUtils.isValidPort;
import static io.opentelemetry.instrumentation.jdbc.internal.parser.UrlParsingUtils.splitHostList;
import static io.opentelemetry.instrumentation.jdbc.internal.parser.UrlParsingUtils.stripIpv6Brackets;
import static java.util.Collections.emptyList;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTargetBuilder;
import io.opentelemetry.instrumentation.jdbc.internal.parser.UrlParsingUtils.HostPort;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/** Extracts MySQL and MariaDB hosts without retaining other per-host connection properties. */
final class MysqlHostParser {

  private static final Logger logger = Logger.getLogger(MysqlHostParser.class.getName());
  private static final Pattern PROPERTY_KEY_PATTERN = Pattern.compile("[a-zA-Z0-9_.%\\s-]*");
  private static final Pattern ADDRESS_KEY_PATTERN = Pattern.compile("[a-zA-Z0-9_.%-]*");

  /** Returns all configured endpoints, or null if any host entry cannot be parsed safely. */
  @Nullable
  static HostList parseHostList(String authority, boolean mysql) {
    if (authority.isEmpty()) {
      return new HostList(emptyList(), false, false);
    }
    List<HostPort> hosts = new ArrayList<>();
    boolean firstHostProperties = false;
    boolean hostProperties = false;
    for (String segment : splitHostList(authority)) {
      String entry = stripUserInfo(segment.trim(), mysql);
      if (entry == null) {
        logger.fine("Unable to parse a configured MySQL/MariaDB host");
        return null;
      }
      HostPort host = parseHost(entry, mysql);
      if (host == null) {
        logger.fine("Unable to parse a configured MySQL/MariaDB host");
        return null;
      }
      boolean properties = entry.startsWith("(") || isAddressHost(entry);
      if (hosts.isEmpty()) {
        firstHostProperties = properties;
      }
      hostProperties |= properties;
      hosts.add(host);
    }
    return new HostList(Collections.unmodifiableList(hosts), firstHostProperties, hostProperties);
  }

  @Nullable
  private static String stripUserInfo(String entry, boolean mysql) {
    int at = entry.indexOf('@');
    if (at < 0) {
      return entry;
    }
    if (isAtInHostProperties(entry, at)) {
      // MariaDB accepts an at sign in an ignored address property; Connector/J splits at it.
      return !mysql && isAddressHost(entry) ? entry : null;
    }
    if (decodeProperty(entry.substring(0, at)) == null) {
      return null;
    }
    String host = entry.substring(at + 1).trim();
    return host.indexOf('@') >= 0 || host.startsWith("(") ? null : host;
  }

  private static boolean isAtInHostProperties(String entry, int at) {
    int start = isAddressHost(entry) ? "address=".length() : 0;
    while (start < at && Character.isWhitespace(entry.charAt(start))) {
      start++;
    }
    if (start >= at || entry.charAt(start) != '(') {
      return false;
    }
    int equals = entry.indexOf('=', start + 1);
    Pattern keyPattern = isAddressHost(entry) ? ADDRESS_KEY_PATTERN : PROPERTY_KEY_PATTERN;
    if (equals < 0
        || equals >= at
        || !keyPattern.matcher(entry.substring(start + 1, equals).trim()).matches()) {
      return false;
    }
    int depth = 0;
    for (int i = start; i < at; i++) {
      char c = entry.charAt(i);
      if (depth == 0 && c != '(' && !Character.isWhitespace(c)) {
        return false;
      }
      if (c == '(') {
        depth++;
      } else if (c == ')') {
        depth--;
      }
    }
    return depth > 0;
  }

  private static boolean isAddressHost(String entry) {
    return entry.regionMatches(true, 0, "address=", 0, "address=".length());
  }

  @Nullable
  private static HostPort parseHost(String entry, boolean mysql) {
    if (entry.startsWith("(")) {
      return mysql ? parseKeyValueHost(entry) : null;
    }
    HostPort host =
        isAddressHost(entry) ? parseAddressHost(entry, mysql) : parseHostPort(entry, mysql);
    return host != null && DbServerTargetBuilder.isValidHost(host.host()) ? host : null;
  }

  @Nullable
  private static HostPort parseHostPort(String entry, boolean mysql) {
    int portStart;
    if (entry.startsWith("[")) {
      int bracketEnd = entry.indexOf(']');
      if (bracketEnd < 0
          || (bracketEnd + 1 < entry.length() && entry.charAt(bracketEnd + 1) != ':')
          || !DbServerTargetBuilder.isValidHost(entry.substring(0, bracketEnd + 1))) {
        return null;
      }
      portStart = bracketEnd + 1 < entry.length() ? bracketEnd + 2 : -1;
    } else {
      int colon = entry.indexOf(':');
      portStart = colon >= 0 && colon == entry.lastIndexOf(':') ? colon + 1 : -1;
    }
    String host = portStart < 0 ? entry : entry.substring(0, portStart - 1);
    host = host.toLowerCase(Locale.ROOT);
    if (mysql) {
      host = decodeProperty(host);
      if (host == null || !host.equals(host.trim())) {
        return null;
      }
    }
    String port = portStart < 0 ? null : entry.substring(portStart);
    if (port != null && mysql) {
      port = decodeProperty(port);
      if (port == null) {
        return null;
      }
    }
    if (port != null && !isValidPort(port)) {
      return null;
    }
    return new HostPort(stripIpv6Brackets(host), port == null ? null : Integer.valueOf(port), null);
  }

  @Nullable
  private static HostPort parseAddressHost(String entry, boolean mysql) {
    HostProperties properties = new HostProperties(mysql, true);
    int start = "address=".length();
    while (start < entry.length()) {
      while (start < entry.length() && Character.isWhitespace(entry.charAt(start))) {
        start++;
      }
      if (start == entry.length()) {
        break;
      }
      if (entry.charAt(start) != '(') {
        return null;
      }
      int end = entry.indexOf(')', start + 1);
      if (end < 0 || !properties.add(entry.substring(start + 1, end), ADDRESS_KEY_PATTERN)) {
        return null;
      }
      start = end + 1;
    }
    return properties.endpoint();
  }

  @Nullable
  private static HostPort parseKeyValueHost(String entry) {
    if (!entry.endsWith(")") || !hasBalancedParentheses(entry)) {
      return null;
    }
    String properties = entry.substring(1, entry.length() - 1);
    // Connector/J treats an unencoded @ as a user-info separator, even inside host properties.
    if (properties.indexOf('@') >= 0 || properties.indexOf('#') >= 0) {
      return null;
    }
    HostProperties parsed = new HostProperties(true, false);
    for (String property : properties.split(",", -1)) {
      if (!parsed.add(property, PROPERTY_KEY_PATTERN)) {
        return null;
      }
    }
    return parsed.endpoint();
  }

  private static boolean hasBalancedParentheses(String entry) {
    int depth = 0;
    for (int i = 0; i < entry.length(); i++) {
      char c = entry.charAt(i);
      if (c == '(') {
        depth++;
      } else if (c == ')' && --depth < 0) {
        return false;
      }
    }
    return depth == 0;
  }

  @Nullable
  private static String decodeProperty(String value) {
    try {
      return URLDecoder.decode(value, "UTF-8");
    } catch (UnsupportedEncodingException | IllegalArgumentException ignored) {
      logger.fine("Unable to decode a MySQL host property");
      return null;
    }
  }

  private MysqlHostParser() {}

  /** Validated endpoints and host syntax used for URL property precedence. */
  static final class HostList {
    private final List<HostPort> hosts;
    private final boolean firstHostProperties;
    private final boolean hostProperties;

    private HostList(List<HostPort> hosts, boolean firstHostProperties, boolean hostProperties) {
      this.hosts = hosts;
      this.firstHostProperties = firstHostProperties;
      this.hostProperties = hostProperties;
    }

    List<HostPort> hosts() {
      return hosts;
    }

    boolean firstHostProperties() {
      return firstHostProperties;
    }

    boolean hasHostProperties() {
      return hostProperties;
    }
  }

  private static final class HostProperties {
    private final boolean decode;
    private final boolean allowZoneIds;
    @Nullable private String host;
    @Nullable private String port;
    private boolean hasHost;
    private boolean hasPort;

    private HostProperties(boolean decode, boolean allowZoneIds) {
      this.decode = decode;
      this.allowZoneIds = allowZoneIds;
    }

    private boolean add(String property, Pattern keyPattern) {
      int equals = property.indexOf('=');
      String rawKey = equals < 0 ? property : property.substring(0, equals);
      if (!keyPattern.matcher(rawKey.trim()).matches()) {
        return false;
      }
      String key = decode ? decodeProperty(rawKey.trim()) : rawKey.trim();
      if (key == null) {
        return false;
      }
      String rawValue = equals < 0 ? null : property.substring(equals + 1).trim();
      if (rawValue != null && key.equalsIgnoreCase("host")) {
        rawValue = rawValue.toLowerCase(Locale.ROOT);
      }
      String value = decode && rawValue != null ? decodeProperty(rawValue) : rawValue;
      if (rawValue != null && value == null) {
        return false;
      }
      if (key.isEmpty()) {
        return value == null || value.isEmpty();
      }
      // Repeated endpoint keys can have case-sensitive driver precedence.
      if (key.equalsIgnoreCase("host")) {
        if (hasHost) {
          return false;
        }
        hasHost = true;
        host = value;
      } else if (key.equalsIgnoreCase("port")) {
        if (hasPort) {
          return false;
        }
        hasPort = true;
        port = value == null || value.isEmpty() ? null : value;
      }
      return true;
    }

    @Nullable
    private HostPort endpoint() {
      if (host == null
          || !host.equals(host.trim())
          || !DbServerTargetBuilder.isValidHost(host)
          || (!allowZoneIds && host.indexOf('%') >= 0)
          || (port != null && !isValidPort(port))) {
        return null;
      }
      return new HostPort(
          stripIpv6Brackets(host), port == null ? null : Integer.valueOf(port), null);
    }
  }
}
