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

  /**
   * Append the {@code type:[subtype:]} prefix that starts a JDBC connection string, without the
   * {@code jdbc:} scheme.
   */
  public static void appendTypePrefix(
      StringBuilder builder, String type, @Nullable String subtype) {
    builder.append(type);
    builder.append(':');
    if (subtype != null) {
      builder.append(subtype);
      builder.append(':');
    }
  }

  /**
   * Append {@code host[:port]}, enclosing a literal IPv6 address in brackets so that the port stays
   * unambiguous.
   */
  public static void appendHostPort(StringBuilder builder, String host, @Nullable Integer port) {
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

  /**
   * Find the index of the first occurrence of any of the specified characters at or after {@code
   * fromIndex}.
   *
   * @param str the string to search
   * @param fromIndex the index to start searching from
   * @param chars the characters to search for
   * @return the index of the first occurrence, or -1 if none found
   */
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

  /**
   * Extract the authority of a URL-shaped connection string, i.e. everything between {@code ://}
   * and the database path, the query string or the fragment.
   *
   * @param url the connection string, with the {@code jdbc:} scheme already removed
   * @return the authority, or null when the connection string has no {@code ://} separator
   */
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
      // A delimiter before the user-info terminator is ambiguous: it may be part of a malformed
      // password. Dropping the group is safer than reporting credential text as a host.
      return null;
    }
    return end < 0 ? url.substring(start) : url.substring(start, end);
  }

  /**
   * Remove the {@code user[:password]@} prefix of an authority.
   *
   * <p>Only an authority that has the URL shape carries user info that way. A driver specific
   * {@code key=value} block, such as the MySQL and MariaDB {@code address=(...)} syntax, spells its
   * credentials out as attributes, and an {@code @} inside one of them belongs to the value rather
   * than to a user info separator. Such an authority is returned unchanged, so that cutting at the
   * last {@code @} cannot leave the tail of a password behind.
   *
   * @param authority the authority to strip
   * @return the authority without user info
   */
  private static String stripUserInfo(String authority) {
    if (!isUrlShapedAuthority(authority)) {
      return authority;
    }
    int at = authority.lastIndexOf('@');
    return at < 0 ? authority : authority.substring(at + 1);
  }

  /**
   * Whether an authority is written as {@code [user[:password]@]host[:port]}, as opposed to a
   * driver specific block of {@code key=value} attributes.
   */
  private static boolean isUrlShapedAuthority(String authority) {
    return authority.indexOf('(') < 0;
  }

  /**
   * Split a comma-separated host list into its entries. Commas inside square brackets (literal IPv6
   * addresses) and inside parentheses (MySQL and MariaDB {@code address=(...)} blocks) are part of
   * an entry rather than separators.
   *
   * @param hostList the host list, without user info
   * @return the individual entries, in the order they appear
   */
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

  private static final Pattern ADDRESS_CREDENTIAL_PATTERN =
      Pattern.compile("\\(\\s*(?:user|password)\\s*=[^)]*\\)");

  /**
   * Remove the credential attributes of the MySQL and MariaDB {@code address=(...)} syntax, e.g.
   * {@code (user=root)} and {@code (password=secret)}.
   *
   * @param authority the authority to sanitize
   * @return the authority without credential attributes
   */
  private static String stripAddressCredentials(String authority) {
    return ADDRESS_CREDENTIAL_PATTERN.matcher(authority).replaceAll("");
  }

  /**
   * Render the sanitized host list of an authority that routes to more than one host.
   *
   * <p>User info and the credentials of MySQL and MariaDB {@code address=(...)} blocks are removed;
   * everything else is kept as configured so that the routing identity survives.
   *
   * <p>Whole credential attributes are removed before any user info is looked for, because a
   * password may hold an {@code @} of its own and cutting the authority at the last {@code @} would
   * otherwise carry the rest of that password into the result.
   *
   * @param authority the authority to sanitize
   * @return the sanitized host list, or null when the authority routes to a single host or still
   *     holds credential material that cannot be told apart from a host
   */
  @Nullable
  public static String sanitizeHostList(String authority) {
    String hostList = stripUserInfo(stripAddressCredentials(authority));
    List<String> entries = splitHostList(hostList);
    if (entries.size() < 2) {
      return null;
    }
    StringBuilder group = new StringBuilder();
    for (String entry : entries) {
      String host = entry.trim();
      if (host.indexOf('@') >= 0) {
        // a host never holds an '@', so an entry that still carries one holds part of a credential
        // that neither of the two steps above recognized, and the whole target is dropped
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
