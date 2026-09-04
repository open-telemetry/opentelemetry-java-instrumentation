/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db.internal;

import static java.util.Collections.emptyList;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;

/**
 * The logical Redis server a client was configured to talk to, rendered as {@code server.address}
 * and {@code server.port}.
 *
 * <p>Configured members are normalized before they are rendered: schemes, credentials, and URL
 * suffixes are stripped, while host names, IPv4 literals, IPv6 literals, and Unix socket paths are
 * preserved. A member that cannot be normalized drops the whole target, because a partial list
 * describes a deployment the client was never pointed at.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class RedisServerTarget {

  private static final int DEFAULT_PORT = 6379;
  private static final int MAX_ENDPOINTS = 5;
  private static final int MAX_ENDPOINT_LIST_LENGTH = 255;

  private final String address;
  @Nullable private final Integer port;

  @Nullable
  public static RedisServerTarget ofLogicalName(@Nullable String name) {
    if (name == null) {
      return null;
    }
    String trimmed = name.trim();
    return isSafeLogicalName(trimmed) ? new RedisServerTarget(trimmed, null) : null;
  }

  @Nullable
  public static RedisServerTarget ofEndpoint(@Nullable String endpoint) {
    Endpoint parsed = Endpoint.parse(endpoint);
    return parsed == null ? null : directTarget(parsed);
  }

  @Nullable
  public static RedisServerTarget ofHostAndPort(@Nullable String host, int port) {
    return ofEndpoint(endpoint(host, port));
  }

  @Nullable
  public static RedisServerTarget ofEndpoints(@Nullable List<String> endpoints) {
    return createFromEndpoints(endpoints, false);
  }

  @Nullable
  public static RedisServerTarget ofUnorderedEndpoints(@Nullable List<String> endpoints) {
    return createFromEndpoints(endpoints, true);
  }

  @Nullable
  public static RedisServerTarget ofEndpointsAndLogicalName(
      @Nullable List<String> endpoints, @Nullable String name) {
    return createFromEndpointsAndLogicalName(endpoints, name, false);
  }

  @Nullable
  public static RedisServerTarget ofUnorderedEndpointsAndLogicalName(
      @Nullable List<String> endpoints, @Nullable String name) {
    return createFromEndpointsAndLogicalName(endpoints, name, true);
  }

  @Nullable
  private static RedisServerTarget createFromEndpointsAndLogicalName(
      @Nullable List<String> endpoints, @Nullable String name, boolean unordered) {
    String logicalName = name == null ? "" : name.trim();
    List<Endpoint> parsed = parseConfiguredEndpoints(endpoints, true);
    if (parsed == null) {
      return null;
    }
    List<String> rendered = new ArrayList<>(parsed.size());
    for (Endpoint endpoint : parsed) {
      rendered.add(endpoint.renderConfigured());
    }
    if (unordered) {
      rendered.sort(String::compareTo);
    }
    if (rendered.isEmpty()) {
      return ofLogicalName(logicalName);
    }
    String address = renderEndpointList(rendered);
    if (address == null) {
      return ofLogicalName(logicalName);
    }
    if (!isSafeLogicalName(logicalName)) {
      return new RedisServerTarget(address, null);
    }
    return new RedisServerTarget(address + "/" + logicalName, null);
  }

  private RedisServerTarget(String address, @Nullable Integer port) {
    this.address = address;
    this.port = port;
  }

  public String getAddress() {
    return address;
  }

  @Nullable
  public Integer getPort() {
    return port;
  }

  @Nullable
  private static RedisServerTarget createFromEndpoints(
      @Nullable List<String> endpoints, boolean unordered) {
    List<Endpoint> parsed = parseConfiguredEndpoints(endpoints, false);
    if (parsed == null || parsed.isEmpty()) {
      return null;
    }
    if (parsed.size() == 1) {
      return directTarget(parsed.get(0));
    }

    return networkTarget(parsed, unordered);
  }

  @Nullable
  private static List<Endpoint> parseConfiguredEndpoints(
      @Nullable List<String> endpoints, boolean allowAllUnrepresentable) {
    if (endpoints == null || endpoints.isEmpty()) {
      return emptyList();
    }
    List<Endpoint> parsed = new ArrayList<>(endpoints.size());
    boolean hasSocket = false;
    boolean unrepresentable = false;
    for (String endpoint : endpoints) {
      Endpoint value = Endpoint.parse(endpoint);
      if (value == null) {
        unrepresentable = true;
        continue;
      }
      if (sharedTarget(value) == null) {
        unrepresentable = true;
        continue;
      }
      parsed.add(value);
      hasSocket |= value.socket;
    }
    if ((unrepresentable && (!allowAllUnrepresentable || !parsed.isEmpty()))
        || (parsed.size() > 1 && hasSocket)) {
      return null;
    }
    return parsed;
  }

  @Nullable
  private static RedisServerTarget directTarget(Endpoint endpoint) {
    return fromSharedTarget(sharedTarget(endpoint));
  }

  @Nullable
  private static RedisServerTarget networkTarget(List<Endpoint> endpoints, boolean unordered) {
    int maxEndpoints = Math.min(MAX_ENDPOINTS, endpoints.size());
    while (maxEndpoints > 0) {
      DbServerTarget target = buildNetworkTarget(endpoints, unordered, maxEndpoints);
      if (target == null) {
        return null;
      }
      if (target.getAddress().length() <= MAX_ENDPOINT_LIST_LENGTH) {
        return fromSharedTarget(target);
      }
      maxEndpoints--;
    }
    return null;
  }

  @Nullable
  private static DbServerTarget buildNetworkTarget(
      List<Endpoint> endpoints, boolean unordered, int maxEndpoints) {
    DbServerTargetBuilder builder =
        DbServerTarget.builder(DEFAULT_PORT).setSorted(unordered).setMaxEndpoints(maxEndpoints);
    for (Endpoint endpoint : endpoints) {
      builder.addEndpoint(endpoint.host, endpoint.port == null ? -1 : endpoint.port);
    }
    return builder.build();
  }

  @Nullable
  private static DbServerTarget sharedTarget(Endpoint endpoint) {
    if (endpoint.socket) {
      return DbServerTarget.unixSocket(endpoint.host);
    }
    return DbServerTarget.builder(DEFAULT_PORT)
        .addEndpoint(endpoint.host, endpoint.port == null ? -1 : endpoint.port)
        .build();
  }

  @Nullable
  private static RedisServerTarget fromSharedTarget(@Nullable DbServerTarget target) {
    return target == null ? null : new RedisServerTarget(target.getAddress(), target.getPort());
  }

  @Nullable
  private static String renderEndpointList(List<String> endpoints) {
    if (endpoints.size() > MAX_ENDPOINTS) {
      endpoints.subList(MAX_ENDPOINTS, endpoints.size()).clear();
    }
    int length = endpoints.size() - 1;
    for (String endpoint : endpoints) {
      length += endpoint.length();
    }
    while (!endpoints.isEmpty() && length > MAX_ENDPOINT_LIST_LENGTH) {
      length -= endpoints.remove(endpoints.size() - 1).length();
      if (!endpoints.isEmpty()) {
        length--;
      }
    }
    return endpoints.isEmpty() ? null : String.join(",", endpoints);
  }

  private static boolean isSafeLogicalName(String value) {
    if (value.isEmpty()) {
      return false;
    }
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (c == '/'
          || c == '\\'
          || c == ','
          || c == '?'
          || c == '#'
          || c == '%'
          || Character.isWhitespace(c)
          || Character.isSpaceChar(c)
          || Character.isISOControl(c)) {
        return false;
      }
    }
    return true;
  }

  public static String endpoint(@Nullable String host, int port) {
    if (host == null) {
      return "";
    }
    StringBuilder builder = new StringBuilder();
    appendHost(builder, host, port >= 0);
    if (port >= 0) {
      builder.append(':').append(port);
    }
    return builder.toString();
  }

  public static String normalizeHostAndPort(@Nullable String value) {
    if (value == null || value.startsWith("[") || value.indexOf("://") >= 0) {
      return value == null ? "" : value;
    }
    int portStart = value.lastIndexOf(':');
    if (portStart <= 0) {
      return value;
    }
    Integer port = Endpoint.parsePort(value.substring(portStart + 1));
    String host = value.substring(0, portStart);
    return port != null && Endpoint.isIpv6Literal(host) ? endpoint(host, port) : value;
  }

  private static void appendHost(StringBuilder builder, String host, boolean hasPort) {
    // a literal IPv6 address is bracketed so that the port stays unambiguous
    if (hasPort && host.indexOf(':') >= 0 && !host.startsWith("[")) {
      builder.append('[').append(host).append(']');
    } else {
      builder.append(host);
    }
  }

  private static final class Endpoint {

    private final String host;
    @Nullable private final Integer port;
    private final boolean socket;

    private Endpoint(String host, @Nullable Integer port, boolean socket) {
      this.host = host;
      this.port = port;
      this.socket = socket;
    }

    @Nullable
    static Endpoint parse(@Nullable String rawEndpoint) {
      if (rawEndpoint == null) {
        return null;
      }
      String value = rawEndpoint.trim();
      if (value.isEmpty()) {
        return null;
      }

      String scheme = null;
      int schemeEnd = value.indexOf("://");
      if (schemeEnd == 0) {
        return null;
      }
      if (schemeEnd > 0) {
        scheme = value.substring(0, schemeEnd);
        value = value.substring(schemeEnd + 3);
      }
      if (value.isEmpty()) {
        return null;
      }

      boolean socket = isSocket(scheme, value);
      if (!socket || value.charAt(0) != '/') {
        int authorityEnd = firstIndexOf(value, '/', '?', '#');
        int credentialsEnd = value.lastIndexOf('@');
        if (credentialsEnd >= 0 && authorityEnd >= 0 && credentialsEnd > authorityEnd) {
          return null;
        }
      }

      if (!socket || scheme != null) {
        value = cutAt(value, '#');
        value = cutAt(value, '?');
      }
      if (value.isEmpty()) {
        return null;
      }
      if (socket) {
        String path = stripSocketCredentials(value);
        return path.isEmpty() || firstIndexOf(path, ',', '?', '#', '@') >= 0
            ? null
            : new Endpoint(path, null, true);
      }

      // everything from the first slash on is the selected database, not part of the endpoint
      String authority = cutAt(value, '/');
      int credentialsEnd = authority.lastIndexOf('@');
      if (credentialsEnd >= 0) {
        authority = authority.substring(credentialsEnd + 1);
      }
      if (authority.isEmpty()) {
        return null;
      }
      return hostAndPort(authority);
    }

    @Nullable
    private static Endpoint hostAndPort(String authority) {
      if (authority.indexOf(',') >= 0 || hasInvalidAuthorityCharacter(authority)) {
        return null;
      }
      if (authority.startsWith("[")) {
        int hostEnd = authority.indexOf(']');
        if (hostEnd <= 1
            || authority.indexOf('[', 1) >= 0
            || authority.indexOf(']', hostEnd + 1) >= 0) {
          return null;
        }
        String host = authority.substring(1, hostEnd);
        if (!isIpv6Literal(host)) {
          return null;
        }
        String rest = authority.substring(hostEnd + 1);
        if (rest.isEmpty()) {
          return new Endpoint(host, null, false);
        }
        if (!rest.startsWith(":")) {
          return null;
        }
        Integer port = parsePort(rest.substring(1));
        return port == null ? null : new Endpoint(host, port, false);
      }
      if (authority.indexOf('[') >= 0 || authority.indexOf(']') >= 0) {
        return null;
      }

      int portStart = authority.indexOf(':');
      int secondColon = portStart < 0 ? -1 : authority.indexOf(':', portStart + 1);
      if (portStart > 0 && secondColon < 0) {
        Integer port = parsePort(authority.substring(portStart + 1));
        return port == null ? null : new Endpoint(authority.substring(0, portStart), port, false);
      }
      // an unbracketed literal IPv6 address has more than one colon and carries no port
      if (secondColon >= 0 && isIpv6Literal(authority)) {
        return new Endpoint(authority, null, false);
      }
      if (portStart >= 0) {
        return null;
      }
      return new Endpoint(authority, null, false);
    }

    private static boolean hasInvalidAuthorityCharacter(String authority) {
      for (int i = 0; i < authority.length(); i++) {
        char c = authority.charAt(i);
        if (Character.isWhitespace(c) || Character.isSpaceChar(c) || Character.isISOControl(c)) {
          return true;
        }
      }
      return false;
    }

    private static boolean isIpv6Literal(String value) {
      try {
        return new URI("redis://[" + value + "]").getHost() != null;
      } catch (URISyntaxException ignored) {
        return false;
      }
    }

    private static boolean isSocket(@Nullable String scheme, String value) {
      if (scheme != null) {
        String normalizedScheme = scheme.toLowerCase(Locale.ROOT);
        return normalizedScheme.endsWith("socket") || normalizedScheme.endsWith("unix");
      }
      return value.charAt(0) == '/';
    }

    private static String stripSocketCredentials(String value) {
      if (value.charAt(0) == '/') {
        return value;
      }
      int credentialsEnd = value.lastIndexOf('@');
      int pathStart = value.indexOf('/');
      if (credentialsEnd >= 0 && (pathStart < 0 || credentialsEnd < pathStart)) {
        return value.substring(credentialsEnd + 1);
      }
      return credentialsEnd < 0 ? value : "";
    }

    private static String cutAt(String value, char separator) {
      int index = value.indexOf(separator);
      return index < 0 ? value : value.substring(0, index);
    }

    private static int firstIndexOf(String value, char... separators) {
      int result = -1;
      for (char separator : separators) {
        int index = value.indexOf(separator);
        if (index >= 0 && (result < 0 || index < result)) {
          result = index;
        }
      }
      return result;
    }

    @Nullable
    private static Integer parsePort(String value) {
      if (value.isEmpty() || value.length() > 5) {
        return null;
      }
      int port = 0;
      for (int i = 0; i < value.length(); i++) {
        char c = value.charAt(i);
        if (c < '0' || c > '9') {
          return null;
        }
        port = port * 10 + (c - '0');
      }
      return port >= 1 && port <= 65535 ? port : null;
    }

    String renderConfigured() {
      StringBuilder builder = new StringBuilder();
      if (socket) {
        builder.append(host);
        return builder.toString();
      }
      appendHost(builder, host, port != null);
      if (port != null) {
        builder.append(':').append(port);
      }
      return builder.toString();
    }
  }
}
