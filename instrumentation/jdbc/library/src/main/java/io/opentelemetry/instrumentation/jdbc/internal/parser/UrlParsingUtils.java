/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal.parser;

import static java.util.Collections.emptyMap;
import static java.util.logging.Level.FINE;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/**
 * Utility methods for parsing JDBC URLs.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class UrlParsingUtils {

  private static final Logger logger = Logger.getLogger(UrlParsingUtils.class.getName());

  // Source: Regular Expressions Cookbook 2nd edition - 8.17.
  // Matches Standard, Mixed or Compressed notation in a wider body of text
  public static final Pattern IPV6_PATTERN =
      Pattern.compile(
          // Non Compressed
          "(?:(?:(?:[A-F0-9]{1,4}:){6}"
              // Compressed with at most 6 colons
              + "|(?=(?:[A-F0-9]{0,4}:){0,6}"
              // and 4 bytes and anchored
              + "(?:[0-9]{1,3}\\.){3}[0-9]{1,3}(?![:.\\w]))"
              // and at most 1 double colon
              + "(([0-9A-F]{1,4}:){0,5}|:)((:[0-9A-F]{1,4}){1,5}:|:)"
              // Compressed with 7 colons and 5 numbers
              + "|::(?:[A-F0-9]{1,4}:){5})"
              // 255.255.255.
              + "(?:(?:25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9]?[0-9])\\.){3}"
              // 255
              + "(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)"
              // Standard
              + "|(?:[A-F0-9]{1,4}:){7}[A-F0-9]{1,4}"
              // Compressed with at most 7 colons and anchored
              + "|(?=(?:[A-F0-9]{0,4}:){0,7}[A-F0-9]{0,4}(?![:.\\w]))"
              // and at most 1 double colon
              + "(([0-9A-F]{1,4}:){1,7}|:)((:[0-9A-F]{1,4}){1,7}|:)"
              // Compressed with 8 colons
              + "|(?:[A-F0-9]{1,4}:){7}:|:(:[A-F0-9]{1,4}){7})(?![:.\\w])",
          Pattern.CASE_INSENSITIVE);

  private UrlParsingUtils() {}

  /**
   * Split a query string into key-value pairs.
   *
   * @param query the query string
   * @param separator the separator between pairs (e.g., "&amp;" or ";")
   * @return a map of key-value pairs
   */
  // Source: https://stackoverflow.com/a/13592567
  public static Map<String, String> splitQuery(String query, String separator) {
    if (query == null || query.isEmpty()) {
      return emptyMap();
    }
    Map<String, String> queryPairs = new LinkedHashMap<>();
    String[] pairs = query.split(separator);
    for (String pair : pairs) {
      try {
        int idx = pair.indexOf("=");
        String key = idx > 0 ? URLDecoder.decode(pair.substring(0, idx), "UTF-8") : pair;
        if (!queryPairs.containsKey(key)) {
          String value =
              idx > 0 && pair.length() > idx + 1
                  ? URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
                  : null;
          queryPairs.put(key, value);
        }
      } catch (UnsupportedEncodingException e) {
        // Ignore.
      }
    }
    return queryPairs;
  }

  /**
   * Parse an integer value, returning null if parsing fails.
   *
   * @param value the string value to parse
   * @return the parsed integer, or null if parsing fails
   */
  public static Integer parsePort(String value) {
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException e) {
      logger.log(FINE, e.getMessage(), e);
      return null;
    }
  }

  /**
   * Build the short URL for db.connection_string attribute.
   *
   * @param type the JDBC type (e.g., "postgresql", "mysql")
   * @param subtype optional subtype (e.g., "thin" for Oracle, "aurora" for MySQL)
   * @param host the host name
   * @param port the port number
   * @return the short URL in format "type:[subtype:]//host:port" or "type:" if no host
   */
  public static String buildShortUrl(
      String type, @Nullable String subtype, @Nullable String host, @Nullable Integer port) {
    StringBuilder url = new StringBuilder(type);
    url.append(':');
    if (subtype != null) {
      url.append(subtype);
      url.append(':');
    }
    if (host != null) {
      url.append("//");
      url.append(host);
      if (port != null) {
        url.append(':');
        url.append(port);
      }
    }
    return url.toString();
  }

  /**
   * Extract parameters from a JDBC URL.
   *
   * @param jdbcUrl the JDBC URL
   * @param startDelimiter the delimiter marking the start of parameters (";" or "?")
   * @param splitSeparator the separator between parameters (";" or "&amp;")
   * @return a map of parameter key-value pairs
   */
  public static Map<String, String> extractParams(
      String jdbcUrl, String startDelimiter, String splitSeparator) {
    int paramLoc = jdbcUrl.indexOf(startDelimiter);
    if (paramLoc < 0) {
      return emptyMap();
    }
    return splitQuery(jdbcUrl.substring(paramLoc + 1), splitSeparator);
  }

  /**
   * Extract semicolon-delimited URL parameters from a JDBC URL.
   *
   * @param jdbcUrl the JDBC URL containing parameters after semicolon
   * @return a map of parameter key-value pairs
   */
  public static Map<String, String> extractSemicolonParams(String jdbcUrl) {
    return extractParams(jdbcUrl, ";", ";");
  }

  /**
   * Extract query-style URL parameters from a JDBC URL.
   *
   * @param jdbcUrl the JDBC URL containing parameters after "?"
   * @param separator the parameter separator (typically "&amp;")
   * @return a map of parameter key-value pairs
   */
  public static Map<String, String> extractQueryParams(String jdbcUrl, String separator) {
    return extractParams(jdbcUrl, "?", separator);
  }

  /**
   * Extract subtype from a JDBC URL of the form "type:subtype://...".
   *
   * <p>For example, "mysql:aurora://host:port/db" returns "aurora", "oceanbase:oracle://host/db"
   * returns "oracle".
   *
   * @param jdbcUrl the JDBC URL
   * @return the subtype, or null if no subtype is present
   */
  @Nullable
  public static String extractSubtype(String jdbcUrl) {
    int protoLoc = jdbcUrl.indexOf("://");
    int typeEndLoc = jdbcUrl.indexOf(':');
    if (protoLoc > 0 && typeEndLoc > 0 && typeEndLoc < protoLoc) {
      return jdbcUrl.substring(typeEndLoc + 1, protoLoc);
    }
    return null;
  }

  /**
   * Find the index of the first occurrence of any of the specified characters.
   *
   * @param str the string to search
   * @param chars the characters to search for
   * @return the index of the first occurrence, or -1 if none found
   */
  public static int indexOfAny(String str, char... chars) {
    int minIndex = -1;
    for (char c : chars) {
      int idx = str.indexOf(c);
      if (idx >= 0 && (minIndex < 0 || idx < minIndex)) {
        minIndex = idx;
      }
    }
    return minIndex;
  }

  /**
   * Result of parsing a server string into host and port components.
   *
   * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
   * at any time.
   */
  public static final class HostPort {
    private final String host;
    @Nullable private final Integer port;
    @Nullable private final String ipv6Address;

    private HostPort(String host, @Nullable Integer port, @Nullable String ipv6Address) {
      this.host = host;
      this.port = port;
      this.ipv6Address = ipv6Address;
    }

    /** The host, with IPv6 addresses bracketed. */
    public String host() {
      return host;
    }

    /** The port, or null if not specified. */
    @Nullable
    public Integer port() {
      return port;
    }

    /** The raw IPv6 address match (without brackets), or null if not IPv6. */
    @Nullable
    public String ipv6Address() {
      return ipv6Address;
    }
  }

  /**
   * Extract host and port from a server string, handling IPv6 addresses. Supports formats: host,
   * host:port, [ipv6], [ipv6]:port, ipv6 (auto-bracketed).
   *
   * @param serverName the server string to parse
   * @return the extracted host and port
   */
  public static HostPort extractHostPort(String serverName) {
    java.util.regex.Matcher ipv6Matcher = IPV6_PATTERN.matcher(serverName);
    boolean isIpv6 = ipv6Matcher.find();
    String ipv6Address = isIpv6 ? ipv6Matcher.group(0) : null;

    int portLoc = -1;
    if (isIpv6) {
      if (serverName.startsWith("[")) {
        portLoc = serverName.indexOf("]:") + 1;
      } else {
        serverName = "[" + serverName + "]";
      }
    } else {
      portLoc = serverName.indexOf(":");
    }

    Integer port = null;
    if (portLoc > 0) {
      port = parsePort(serverName.substring(portLoc + 1));
      serverName = serverName.substring(0, portLoc);
    }

    return new HostPort(serverName, port, ipv6Address);
  }

  /**
   * Lightweight wrapper for URL parameters that provides cleaner access patterns.
   *
   * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
   * at any time.
   */
  public static final class UrlParams {
    private static final UrlParams EMPTY = new UrlParams(emptyMap());

    private final Map<String, String> params;

    private UrlParams(Map<String, String> params) {
      this.params = params;
    }

    /** Parse semicolon-delimited parameters (e.g., "user=foo;password=bar"). */
    public static UrlParams fromSemicolon(@Nullable String paramString) {
      if (paramString == null || paramString.isEmpty()) {
        return EMPTY;
      }
      return new UrlParams(splitQuery(paramString, ";"));
    }

    /** Get parameter value, or null if not present. */
    @Nullable
    public String get(String key) {
      return params.get(key);
    }

    /** Get parameter value, or default if not present. */
    public String getOrDefault(String key, String defaultValue) {
      String value = params.get(key);
      return value != null ? value : defaultValue;
    }
  }
}
