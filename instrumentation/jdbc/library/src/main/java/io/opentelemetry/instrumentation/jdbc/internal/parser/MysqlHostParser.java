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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
      HostPort host = entry == null ? null : parseHost(entry, mysql);
      if (entry == null || host == null) {
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
    HostPort host =
        entry.startsWith("(") || isAddressHost(entry)
            ? parseHostProperties(entry, mysql)
            : parseHostPort(entry, mysql);
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
  private static HostPort parseHostProperties(String entry, boolean mysql) {
    boolean address = isAddressHost(entry);
    if (!address
        && (!mysql
            || !entry.endsWith(")")
            || !hasBalancedParentheses(entry)
            || entry.indexOf('@') >= 0
            || entry.indexOf('#') >= 0)) {
      return null;
    }
    Map<String, String> properties = new HashMap<>();
    int limit = address ? entry.length() : entry.length() - 1;
    int start = address ? "address=".length() : 1;
    Pattern keyPattern = address ? ADDRESS_KEY_PATTERN : PROPERTY_KEY_PATTERN;
    while (start < limit) {
      if (address) {
        while (start < limit && Character.isWhitespace(entry.charAt(start))) {
          start++;
        }
        if (start == limit) {
          break;
        }
        if (entry.charAt(start++) != '(') {
          return null;
        }
      }
      int end = entry.indexOf(address ? ')' : ',', start);
      if (end < 0) {
        if (address) {
          return null;
        }
        end = limit;
      }
      String property = entry.substring(start, end);
      int equals = property.indexOf('=');
      String key = (equals < 0 ? property : property.substring(0, equals)).trim();
      if (!keyPattern.matcher(key).matches()) {
        return null;
      }
      key = mysql ? decodeProperty(key) : key;
      if (key == null) {
        return null;
      }
      if (key.equalsIgnoreCase("host")) {
        key = "host";
      } else if (key.equalsIgnoreCase("port")) {
        key = "port";
      }
      String value = equals < 0 ? "" : property.substring(equals + 1).trim();
      value = key.equals("host") ? value.toLowerCase(Locale.ROOT) : value;
      value = mysql ? decodeProperty(value) : value;
      if (value == null || (key.isEmpty() && !value.isEmpty())) {
        return null;
      }
      if (key.equals("host") || key.equals("port")) {
        // Repeated endpoint keys can have case-sensitive driver precedence.
        if (properties.containsKey(key)) {
          return null;
        }
        properties.put(key, value);
      }
      start = end + 1;
    }
    String host = properties.get("host");
    String port = properties.get("port");
    port = port == null || port.isEmpty() ? null : port;
    if (host == null
        || !host.equals(host.trim())
        || !DbServerTargetBuilder.isValidHost(host)
        || (!address && host.indexOf('%') >= 0)
        || (port != null && !isValidPort(port))) {
      return null;
    }
    return new HostPort(stripIpv6Brackets(host), port == null ? null : Integer.valueOf(port), null);
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
}
