/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal.parser;

import static io.opentelemetry.instrumentation.jdbc.internal.parser.UrlParsingUtils.extractAddressHostPort;
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
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/** Extracts MySQL and MariaDB hosts without retaining other per-host connection properties. */
final class MysqlHostParser {

  private static final Logger logger = Logger.getLogger(MysqlHostParser.class.getName());
  private static final Pattern PROPERTY_KEY_PATTERN = Pattern.compile("[a-zA-Z0-9_.%\\s-]*");
  private static final Pattern HOST_PROPERTIES_PATTERN =
      Pattern.compile("(^|,)\\s*(?:address\\s*=\\s*)?\\(", Pattern.CASE_INSENSITIVE);

  /** Returns all configured endpoints, or null if any host entry cannot be parsed safely. */
  @Nullable
  static List<HostPort> parseHostList(String authority, boolean allowKeyValueHosts) {
    if (authority.isEmpty()) {
      return emptyList();
    }
    int userInfoEnd = authority.lastIndexOf('@');
    // Parentheses in ordinary user info are not host-property blocks.
    if (userInfoEnd >= 0
        && authority.indexOf('(', userInfoEnd + 1) < 0
        && !isAtInHostProperties(authority, userInfoEnd)) {
      authority = authority.substring(userInfoEnd + 1);
    }
    List<HostPort> hosts = new ArrayList<>();
    for (String entry : splitHostList(authority)) {
      HostPort host = parseHost(entry.trim(), allowKeyValueHosts);
      if (host == null) {
        logger.fine("Unable to parse a configured MySQL/MariaDB host");
        return null;
      }
      hosts.add(host);
    }
    return Collections.unmodifiableList(hosts);
  }

  private static boolean isAtInHostProperties(String authority, int at) {
    Matcher properties = HOST_PROPERTIES_PATTERN.matcher(authority);
    while (properties.find() && properties.end() <= at) {
      int depth = 1;
      for (int i = properties.end(); i < at; i++) {
        char c = authority.charAt(i);
        if (depth == 0 && c != '(' && !Character.isWhitespace(c)) {
          break;
        }
        if (c == '(') {
          depth++;
        } else if (c == ')') {
          depth--;
        }
      }
      if (depth > 0) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  private static HostPort parseHost(String entry, boolean allowKeyValueHosts) {
    if (entry.startsWith("(")) {
      return allowKeyValueHosts ? parseKeyValueHost(entry) : null;
    }
    HostPort host = entry.startsWith("address=") ? parseAddressHost(entry) : parseHostPort(entry);
    return host != null && DbServerTargetBuilder.isValidHost(host.host()) ? host : null;
  }

  @Nullable
  private static HostPort parseHostPort(String entry) {
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
    if (portStart >= 0 && !isValidPort(entry.substring(portStart))) {
      return null;
    }
    String host = portStart < 0 ? entry : entry.substring(0, portStart - 1);
    Integer port = portStart < 0 ? null : Integer.valueOf(entry.substring(portStart));
    return new HostPort(stripIpv6Brackets(host), port, null);
  }

  @Nullable
  private static HostPort parseAddressHost(String entry) {
    return hasBalancedParentheses(entry) ? extractAddressHostPort(entry) : null;
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
    String host = null;
    String port = null;
    boolean hasHost = false;
    boolean hasPort = false;
    for (String property : properties.split(",", -1)) {
      int equals = property.indexOf('=');
      String rawKey = equals < 0 ? property : property.substring(0, equals);
      if (!PROPERTY_KEY_PATTERN.matcher(rawKey).matches()) {
        return null;
      }
      String key = decodeProperty(rawKey.trim());
      // Connector/J accepts a property without a value and skips empty properties.
      String value = equals < 0 ? null : decodeProperty(property.substring(equals + 1).trim());
      if (key == null || (equals >= 0 && value == null)) {
        return null;
      }
      if (key.isEmpty()) {
        if (value != null && !value.isEmpty()) {
          return null;
        }
        continue;
      }
      // Lowercased URLs cannot distinguish repeated keys from case-sensitive driver alternatives.
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
    if (host == null
        || !host.equals(host.trim())
        || !DbServerTargetBuilder.isValidHost(host)
        || host.indexOf('%') >= 0
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
}
