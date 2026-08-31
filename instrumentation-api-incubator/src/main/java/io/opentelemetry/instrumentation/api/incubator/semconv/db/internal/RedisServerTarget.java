/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;

@SuppressWarnings("OtelInternalJavadoc")
public final class RedisServerTarget {

  private final String address;
  @Nullable private final Integer port;

  private RedisServerTarget(String address, @Nullable Integer port) {
    this.address = address;
    this.port = port;
  }

  @Nullable
  public static RedisServerTarget ofLogicalName(@Nullable String name) {
    if (name == null) {
      return null;
    }
    String trimmed = name.trim();
    return trimmed.isEmpty() ? null : new RedisServerTarget(trimmed, null);
  }

  @Nullable
  public static RedisServerTarget ofEndpoint(@Nullable String endpoint) {
    Endpoint parsed = Endpoint.parse(endpoint);
    return parsed == null ? null : new RedisServerTarget(parsed.host, parsed.port);
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
  private static RedisServerTarget createFromEndpoints(
      @Nullable List<String> endpoints, boolean unordered) {
    if (endpoints == null || endpoints.isEmpty()) {
      return null;
    }
    List<Endpoint> parsed = new ArrayList<>(endpoints.size());
    for (String endpoint : endpoints) {
      Endpoint value = Endpoint.parse(endpoint);
      if (value != null) {
        parsed.add(value);
      }
    }
    if (parsed.isEmpty()) {
      return null;
    }
    if (parsed.size() == 1) {
      Endpoint only = parsed.get(0);
      return new RedisServerTarget(only.host, only.port);
    }
    List<String> rendered = new ArrayList<>(parsed.size());
    for (Endpoint endpoint : parsed) {
      rendered.add(endpoint.render());
    }
    if (unordered) {
      Collections.sort(rendered);
    }
    return new RedisServerTarget(String.join(",", rendered), null);
  }

  @Nullable
  public static RedisServerTarget ofUnorderedEndpointsAndLogicalName(
      @Nullable List<String> endpoints, @Nullable String name) {
    String logicalName = name == null ? "" : name.trim();
    List<String> rendered = new ArrayList<>();
    if (endpoints != null) {
      for (String endpoint : endpoints) {
        Endpoint parsed = Endpoint.parse(endpoint);
        if (parsed != null) {
          rendered.add(parsed.render());
        }
      }
    }
    Collections.sort(rendered);
    if (rendered.isEmpty()) {
      return ofLogicalName(logicalName);
    }
    if (logicalName.isEmpty()) {
      return new RedisServerTarget(String.join(",", rendered), null);
    }
    return new RedisServerTarget(String.join(",", rendered) + "/" + logicalName, null);
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

  private static void appendHost(StringBuilder builder, String host, boolean hasPort) {
    // a literal IPv6 address is bracketed so that the port stays unambiguous
    if (hasPort && host.indexOf(':') >= 0 && !host.startsWith("[")) {
      builder.append('[').append(host).append(']');
    } else {
      builder.append(host);
    }
  }

  public String getAddress() {
    return address;
  }

  @Nullable
  public Integer getPort() {
    return port;
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
        return path.isEmpty() ? null : new Endpoint(path, null, true);
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
      if (authority.startsWith("[")) {
        int hostEnd = authority.indexOf(']');
        if (hostEnd <= 1
            || authority.indexOf('[', 1) >= 0
            || authority.indexOf(']', hostEnd + 1) >= 0) {
          return null;
        }
        String host = authority.substring(1, hostEnd);
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
      // an unbracketed literal IPv6 address has more than one colon and carries no port
      if (portStart > 0 && authority.indexOf(':', portStart + 1) < 0) {
        Integer port = parsePort(authority.substring(portStart + 1));
        return port == null ? null : new Endpoint(authority.substring(0, portStart), port, false);
      }
      return new Endpoint(authority, null, false);
    }

    private static boolean isSocket(@Nullable String scheme, String value) {
      if (scheme != null) {
        return scheme.endsWith("socket") || scheme.endsWith("unix");
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
      return port <= 65535 ? port : null;
    }

    String render() {
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
