/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal.parser;

import static java.util.Collections.emptyMap;
import static java.util.logging.Level.FINE;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
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

  private static final Pattern ADDRESS_HOST_PATTERN =
      Pattern.compile("\\(\\s*host\\s*=\\s*([^)]*?)\\s*\\)", Pattern.CASE_INSENSITIVE);
  private static final Pattern ADDRESS_PORT_PATTERN =
      Pattern.compile("\\(\\s*port\\s*=\\s*([\\d]+)\\s*\\)", Pattern.CASE_INSENSITIVE);

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
      } catch (UnsupportedEncodingException ignored) {
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
  @Nullable
  public static Integer parsePort(@Nullable String value) {
    if (value == null) {
      return null;
    }
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
    StringBuilder url = new StringBuilder();
    appendTypePrefix(url, type, subtype);
    if (host != null) {
      url.append("//");
      appendHostPort(url, host, port);
    }
    return url.toString();
  }

  public static void appendTypePrefix(
      StringBuilder builder, String type, @Nullable String subtype) {
    builder.append(type);
    builder.append(':');
    if (subtype != null) {
      builder.append(subtype);
      builder.append(':');
    }
  }

  public static void appendHostPort(StringBuilder builder, String host, @Nullable Integer port) {
    // Brackets keep an IPv6 literal unambiguous when a port follows.
    if (host.contains(":") && !host.startsWith("[")) {
      builder.append('[');
      builder.append(host);
      builder.append(']');
    } else {
      builder.append(host);
    }
    if (port != null) {
      builder.append(':');
      builder.append(port);
    }
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
    return indexOfAny(str, 0, chars);
  }

  public static int indexOfAny(String str, int fromIndex, char... chars) {
    for (int i = Math.max(fromIndex, 0); i < str.length(); i++) {
      char c = str.charAt(i);
      for (char match : chars) {
        if (c == match) {
          return i;
        }
      }
    }
    return -1;
  }

  @Nullable
  public static String extractAuthority(String url) {
    int protoLoc = url.indexOf("://");
    if (protoLoc < 0) {
      return null;
    }
    int start = protoLoc + 3;
    int end = indexOfAny(url, start, '/', '?', '#');
    int lastAt = url.lastIndexOf('@');
    if (lastAt >= start && end >= 0 && lastAt > end) {
      int possibleAuthorityEnd = indexOfAny(url, lastAt + 1, '/', '?', '#');
      possibleAuthorityEnd = possibleAuthorityEnd < 0 ? url.length() : possibleAuthorityEnd;
      int commaAfterAt = url.indexOf(',', lastAt + 1);
      int commaBeforeEnd = url.indexOf(',', start);
      boolean atInQueryParameter = isAtInQueryParameter(url, end, lastAt);
      if (!atInQueryParameter
          && ((commaAfterAt >= 0 && commaAfterAt < possibleAuthorityEnd)
              || (commaBeforeEnd >= 0 && commaBeforeEnd < end))) {
        // Comma-separated text around an early delimiter may be part of a malformed password.
        // Dropping the group is safer than reporting credential text as a host.
        return null;
      }
    }
    return end < 0 ? url.substring(start) : url.substring(start, end);
  }

  @Nullable
  public static String extractAuthorityWithQueryAt(String url) {
    int authorityStart = url.indexOf("://");
    authorityStart = authorityStart < 0 ? 0 : authorityStart + 3;
    int authorityEnd = indexOfAny(url, authorityStart, '/', '?', '#');
    if (authorityEnd < 0) {
      return null;
    }
    int queryStart = url.indexOf('?', authorityEnd);
    int credentialsEnd = url.lastIndexOf('@');
    int parameterStart =
        queryStart < 0 ? -1 : Math.max(queryStart, url.lastIndexOf('&', credentialsEnd)) + 1;
    int parameterEnd = url.indexOf('&', credentialsEnd);
    if (parameterEnd < 0) {
      parameterEnd = url.length();
    }
    int equals = parameterStart < 0 ? -1 : url.indexOf('=', parameterStart);
    if (queryStart < 0
        || credentialsEnd < queryStart
        || equals < parameterStart
        || equals > credentialsEnd) {
      return null;
    }

    String authority = url.substring(authorityStart, authorityEnd);
    if (!hasValidHostPorts(authority)) {
      return null;
    }
    String suffix = url.substring(credentialsEnd + 1, parameterEnd);
    int suffixAuthorityEnd = indexOfAny(suffix, '/', '?', '#');
    String suffixAuthority =
        suffixAuthorityEnd < 0 ? suffix : suffix.substring(0, suffixAuthorityEnd);
    if (hasValidHostPorts(suffixAuthority)) {
      return null;
    }
    if (authority.indexOf(',') >= 0) {
      return authority;
    }

    return suffix.indexOf('/') < 0 && suffix.indexOf(',') < 0 ? authority : null;
  }

  private static boolean isAtInQueryParameter(String url, int authorityEnd, int at) {
    int queryStart = url.indexOf('?', authorityEnd);
    if (queryStart < 0 || queryStart > at) {
      return false;
    }
    int fragmentStart = url.indexOf('#', queryStart);
    if (fragmentStart >= 0 && fragmentStart < at) {
      return false;
    }
    int parameterStart = Math.max(queryStart, url.lastIndexOf('&', at)) + 1;
    int equals = url.indexOf('=', parameterStart);
    if (equals < parameterStart || equals > at) {
      return false;
    }
    if (queryStart > authorityEnd) {
      return true;
    }
    int parameterEnd = indexOfAny(url, at + 1, '&', '#');
    parameterEnd = parameterEnd < 0 ? url.length() : parameterEnd;
    String suffix = url.substring(at + 1, parameterEnd);
    return suffix.indexOf('/') < 0 && suffix.indexOf(',') < 0;
  }

  private static String stripUserInfo(String authority) {
    // address=(...) credentials use key/value syntax, where '@' belongs to the value.
    if (!isUrlShapedAuthority(authority)) {
      return authority;
    }
    int at = authority.lastIndexOf('@');
    return at < 0 ? authority : authority.substring(at + 1);
  }

  private static boolean isUrlShapedAuthority(String authority) {
    return authority.indexOf('(') < 0;
  }

  // IPv6 brackets and address=(...) blocks may contain commas.
  private static List<String> splitHostList(String hostList) {
    List<String> entries = new ArrayList<>();
    int depth = 0;
    int start = 0;
    for (int i = 0; i < hostList.length(); i++) {
      char c = hostList.charAt(i);
      if (c == '[' || c == '(') {
        depth++;
      } else if (c == ']' || c == ')') {
        depth--;
      } else if (c == ',' && depth <= 0) {
        entries.add(hostList.substring(start, i));
        start = i + 1;
      }
    }
    entries.add(hostList.substring(start));
    return entries;
  }

  @Nullable
  private static String sanitizeHostEntry(String entry) {
    String host = entry.trim();
    if (!host.regionMatches(true, 0, "address=", 0, "address=".length())) {
      return host.indexOf('=') < 0 && isValidHostPort(host) ? host : null;
    }

    Matcher hostMatcher = ADDRESS_HOST_PATTERN.matcher(host);
    if (!hostMatcher.find()) {
      return null;
    }
    String addressHost = hostMatcher.group(1).trim();
    if (addressHost.isEmpty()) {
      return null;
    }
    StringBuilder sanitized = new StringBuilder("address=(host=");
    sanitized.append(addressHost).append(')');

    Matcher portMatcher = ADDRESS_PORT_PATTERN.matcher(host);
    if (portMatcher.find()) {
      sanitized.append("(port=").append(portMatcher.group(1)).append(')');
    }
    return sanitized.toString();
  }

  private static boolean hasValidHostPorts(String authority) {
    for (String endpoint : authority.split(",")) {
      if (!isValidHostPort(endpoint.trim())) {
        return false;
      }
    }
    return true;
  }

  private static boolean isValidHostPort(String value) {
    if (value.isEmpty() || value.indexOf('=') >= 0) {
      return false;
    }
    int closingBracket = value.startsWith("[") ? value.indexOf(']') : -1;
    if (closingBracket >= 0) {
      String rest = value.substring(closingBracket + 1);
      return rest.isEmpty() || (rest.startsWith(":") && parsePort(rest.substring(1)) != null);
    }
    int colon = value.lastIndexOf(':');
    return colon < 0
        || value.indexOf(':') != colon
        || parsePort(value.substring(colon + 1)) != null;
  }

  @Nullable
  public static String sanitizeHostList(String authority) {
    String hostList = stripUserInfo(authority);
    List<String> entries = splitHostList(hostList);
    if (entries.size() < 2) {
      return null;
    }
    StringBuilder group = new StringBuilder();
    for (String entry : entries) {
      String host = sanitizeHostEntry(entry);
      if (host == null || host.indexOf('@') >= 0) {
        // Do not risk reporting unrecognized credential text as a host.
        return null;
      }
      if (group.length() > 0) {
        group.append(',');
      }
      group.append(host);
    }
    return group.toString();
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

    /** The host, with IPv6 addresses unbracketed. */
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
   * host:port, [ipv6], [ipv6]:port, ipv6.
   *
   * <p>The returned host never carries the enclosing brackets of a literal IPv6 address, matching
   * the {@code server.address} semantic convention, which holds the address alone.
   *
   * @param serverName the server string to parse
   * @return the extracted host and port
   */
  public static HostPort extractHostPort(String serverName) {
    Matcher ipv6Matcher = IPV6_PATTERN.matcher(serverName);
    boolean isIpv6 = ipv6Matcher.find();
    String ipv6Address = isIpv6 ? ipv6Matcher.group(0) : null;

    int portLoc = -1;
    if (isIpv6) {
      if (serverName.startsWith("[")) {
        portLoc = serverName.indexOf("]:") + 1;
      }
    } else {
      portLoc = serverName.indexOf(":");
    }

    Integer port = null;
    if (portLoc > 0) {
      port = parsePort(serverName.substring(portLoc + 1));
      serverName = serverName.substring(0, portLoc);
    }

    return new HostPort(stripIpv6Brackets(serverName), port, ipv6Address);
  }

  /**
   * Remove the enclosing brackets from a literal IPv6 address, e.g. {@code [::1]} becomes {@code
   * ::1}. Any other value is returned unchanged.
   *
   * @param host the host to unbracket
   * @return the host without enclosing brackets
   */
  public static String stripIpv6Brackets(String host) {
    if (host.length() > 1 && host.charAt(0) == '[' && host.endsWith("]")) {
      return host.substring(1, host.length() - 1);
    }
    return host;
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
