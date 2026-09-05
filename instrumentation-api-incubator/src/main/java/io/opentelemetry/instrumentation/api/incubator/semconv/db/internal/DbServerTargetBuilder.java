/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db.internal;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Collects the endpoints a database client was configured with and renders them as a single {@link
 * DbServerTarget}.
 *
 * <p>Every endpoint is validated as a host name, an IPv4 literal, or an IPv6 literal without any
 * name resolution. When one endpoint cannot be validated the whole target is dropped, because a
 * partial list describes a deployment the client was never pointed at. Endpoints are validated
 * before the list is truncated, so an unsafe endpoint beyond the cap still drops the target.
 *
 * <p>Duplicate endpoints are kept. A single endpoint is rendered as a bare host with its port
 * reported separately, and it is reported only when it differs from the default port. Several
 * endpoints are rendered as a comma separated list. Their ports are omitted when every endpoint
 * listens on its default port. Otherwise known ports are included, while endpoints without either a
 * configured or known default port remain bare.
 *
 * <p>Instances are not thread safe.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public class DbServerTargetBuilder {

  private static final int DEFAULT_MAX_ENDPOINTS = 5;
  private static final int MIN_PORT = 1;
  private static final int MAX_PORT = 65535;
  private static final int MAX_HOST_NAME_LENGTH = 253;
  private static final int MAX_HOST_NAME_SEGMENT_LENGTH = 63;

  @Nullable private final Integer defaultPort;
  private final List<Endpoint> endpoints = new ArrayList<>();
  private int maxEndpoints = DEFAULT_MAX_ENDPOINTS;
  private boolean sorted;
  private boolean portAlwaysInline;
  @Nullable private String suffix;
  private boolean complete = true;

  DbServerTargetBuilder(@Nullable Integer defaultPort) {
    this.defaultPort = defaultPort;
  }

  /**
   * Sort the rendered endpoints in natural string order. Default is to preserve the configured
   * order. Turn sorting on only when endpoint order carries no semantic meaning.
   */
  @CanIgnoreReturnValue
  public DbServerTargetBuilder setSorted(boolean sorted) {
    this.sorted = sorted;
    return this;
  }

  /** Render at most {@code maxEndpoints} endpoints. Default is 5. */
  @CanIgnoreReturnValue
  public DbServerTargetBuilder setMaxEndpoints(int maxEndpoints) {
    if (maxEndpoints < 1) {
      throw new IllegalArgumentException("maxEndpoints must be positive");
    }
    this.maxEndpoints = maxEndpoints;
    return this;
  }

  /**
   * Always render ports inside the address instead of reporting a single endpoint's port
   * separately. Default is to report a single endpoint's port separately. Turn this on for a target
   * that names a discovery or coordination service rather than the server that runs the operation.
   */
  @CanIgnoreReturnValue
  public DbServerTargetBuilder setPortAlwaysInline(boolean portAlwaysInline) {
    this.portAlwaysInline = portAlwaysInline;
    return this;
  }

  /**
   * Append {@code suffix} to the address as a path segment, identifying the logical subset of the
   * endpoints that the client selects. A suffix that is not a safe path segment is dropped and the
   * remaining target is kept. Ports are always rendered inline when a suffix is set, so that the
   * suffix cannot be read as part of an endpoint.
   */
  @CanIgnoreReturnValue
  public DbServerTargetBuilder setSuffix(@Nullable String suffix) {
    this.suffix = sanitizePathSegment(suffix);
    return this;
  }

  /**
   * Add an endpoint. A negative {@code port} means that the endpoint has no configured port. The
   * target's default port is used when known.
   */
  @CanIgnoreReturnValue
  public DbServerTargetBuilder addEndpoint(@Nullable String host, int port) {
    return addEndpointInternal(host, port, defaultPort);
  }

  /**
   * Add an endpoint whose default port differs from the target's default port, such as an endpoint
   * whose default port follows from its scheme.
   */
  @CanIgnoreReturnValue
  public DbServerTargetBuilder addEndpoint(@Nullable String host, int port, int defaultPort) {
    return addEndpointInternal(host, port, defaultPort);
  }

  /**
   * Add an endpoint from a socket address. The host is read without resolving it, so an address
   * created from a name keeps that name and an address created from a numeric address keeps its
   * literal form.
   */
  @CanIgnoreReturnValue
  public DbServerTargetBuilder addEndpoint(@Nullable InetSocketAddress address) {
    if (address == null) {
      complete = false;
      return this;
    }
    return addEndpoint(address.getHostString(), address.getPort());
  }

  @CanIgnoreReturnValue
  private DbServerTargetBuilder addEndpointInternal(
      @Nullable String host, int port, @Nullable Integer defaultPort) {
    String sanitizedHost = sanitizeHost(host);
    Integer effectivePort = port < 0 ? defaultPort : Integer.valueOf(port);
    if (sanitizedHost == null || (effectivePort != null && !isValidPort(effectivePort))) {
      complete = false;
      return this;
    }
    endpoints.add(
        new Endpoint(
            sanitizedHost,
            effectivePort,
            port < 0 || (defaultPort != null && port == defaultPort)));
    return this;
  }

  /** Returns the target, or {@code null} when it cannot be rendered safely. */
  @Nullable
  public DbServerTarget build() {
    if (!complete || endpoints.isEmpty()) {
      return null;
    }
    boolean inlinePorts = inlinePorts();
    if (!inlinePorts && endpoints.size() == 1) {
      Endpoint endpoint = endpoints.get(0);
      return target(endpoint.host, endpoint.defaultPort ? null : endpoint.port);
    }
    return target(render(inlinePorts), null);
  }

  private boolean inlinePorts() {
    if (portAlwaysInline || suffix != null) {
      return true;
    }
    if (endpoints.size() == 1) {
      return false;
    }
    for (Endpoint endpoint : endpoints) {
      if (!endpoint.defaultPort) {
        return true;
      }
    }
    return false;
  }

  private String render(boolean includePort) {
    List<String> rendered = new ArrayList<>(endpoints.size());
    for (Endpoint endpoint : endpoints) {
      rendered.add(
          includePort && endpoint.port != null
              ? renderHostAndPort(endpoint.host, endpoint.port)
              : endpoint.host);
    }
    if (sorted) {
      rendered.sort(String::compareTo);
    }
    return String.join(",", rendered.subList(0, Math.min(maxEndpoints, rendered.size())));
  }

  private DbServerTarget target(String address, @Nullable Integer port) {
    return new DbServerTarget(suffix == null ? address : address + "/" + suffix, port);
  }

  private static String renderHostAndPort(String host, int port) {
    StringBuilder endpoint = new StringBuilder();
    // brackets keep a literal IPv6 address separate from the port that follows it
    if (host.indexOf(':') >= 0) {
      endpoint.append('[').append(host).append(']');
    } else {
      endpoint.append(host);
    }
    return endpoint.append(':').append(port).toString();
  }

  private static boolean isValidPort(int port) {
    return port >= MIN_PORT && port <= MAX_PORT;
  }

  @Nullable
  private static String sanitizeHost(@Nullable String host) {
    if (host == null) {
      return null;
    }
    String sanitized = host.trim();
    boolean bracketed =
        sanitized.length() >= 2
            && sanitized.charAt(0) == '['
            && sanitized.charAt(sanitized.length() - 1) == ']';
    if (bracketed) {
      sanitized = sanitized.substring(1, sanitized.length() - 1).trim();
    }
    if (sanitized.isEmpty() || sanitized.indexOf('[') >= 0 || sanitized.indexOf(']') >= 0) {
      return null;
    }
    if (sanitized.indexOf(':') >= 0) {
      return isIpv6Literal(sanitized) ? sanitized : null;
    }
    if (bracketed) {
      return null;
    }
    if (looksLikeIpv4Literal(sanitized)) {
      return isIpv4Literal(sanitized) ? sanitized : null;
    }
    return isHostName(sanitized) ? sanitized : null;
  }

  private static boolean isIpv6Literal(String host) {
    int zoneStart = host.indexOf('%');
    String literal = zoneStart < 0 ? host : host.substring(0, zoneStart);
    if (zoneStart >= 0 && !isZoneId(host.substring(zoneStart + 1))) {
      return false;
    }
    for (int i = 0; i < literal.length(); i++) {
      char c = literal.charAt(i);
      if (c != ':' && c != '.' && !isAsciiHexDigit(c)) {
        return false;
      }
    }
    int ipv4Start = literal.lastIndexOf(':') + 1;
    if (literal.indexOf('.') >= 0 && !isIpv4Literal(literal.substring(ipv4Start))) {
      return false;
    }
    try {
      // URI parses a bracketed literal without resolving any name
      return new URI("db://[" + literal + "]").getHost() != null;
    } catch (URISyntaxException ignored) {
      return false;
    }
  }

  private static boolean isZoneId(String zoneId) {
    if (zoneId.isEmpty()) {
      return false;
    }
    for (int i = 0; i < zoneId.length(); i++) {
      if (!isUnreserved(zoneId.charAt(i))) {
        return false;
      }
    }
    return true;
  }

  private static boolean looksLikeIpv4Literal(String host) {
    for (int i = 0; i < host.length(); i++) {
      char c = host.charAt(i);
      if (c != '.' && !isAsciiDigit(c)) {
        return false;
      }
    }
    return host.indexOf('.') >= 0;
  }

  private static boolean isIpv4Literal(String host) {
    int parts = 0;
    int digits = 0;
    int value = 0;
    for (int i = 0; i <= host.length(); i++) {
      char c = i == host.length() ? '.' : host.charAt(i);
      if (c == '.') {
        // a part with a leading zero reads as octal to some resolvers
        if (digits == 0 || (digits > 1 && host.charAt(i - digits) == '0') || value > 255) {
          return false;
        }
        parts++;
        digits = 0;
        value = 0;
      } else {
        if (++digits > 3) {
          return false;
        }
        value = value * 10 + c - '0';
      }
    }
    return parts == 4;
  }

  private static boolean isHostName(String host) {
    int length = host.endsWith(".") ? host.length() - 1 : host.length();
    if (length <= 0 || length > MAX_HOST_NAME_LENGTH) {
      return false;
    }
    int segmentStart = 0;
    for (int i = 0; i <= length; i++) {
      if (i != length && host.charAt(i) != '.') {
        continue;
      }
      if (!isHostNameSegment(host, segmentStart, i)) {
        return false;
      }
      segmentStart = i + 1;
    }
    return true;
  }

  private static boolean isHostNameSegment(String host, int start, int end) {
    if (end - start < 1 || end - start > MAX_HOST_NAME_SEGMENT_LENGTH) {
      return false;
    }
    if (!isAsciiLetterOrDigit(host.charAt(start)) || !isAsciiLetterOrDigit(host.charAt(end - 1))) {
      return false;
    }
    for (int i = start + 1; i < end - 1; i++) {
      char c = host.charAt(i);
      if (c != '-' && c != '_' && !isAsciiLetterOrDigit(c)) {
        return false;
      }
    }
    return true;
  }

  @Nullable
  private static String sanitizePathSegment(@Nullable String segment) {
    if (segment == null) {
      return null;
    }
    String sanitized = segment.trim();
    if (sanitized.isEmpty() || ".".equals(sanitized) || "..".equals(sanitized)) {
      return null;
    }
    for (int i = 0; i < sanitized.length(); i++) {
      if (!isUnreserved(sanitized.charAt(i))) {
        return null;
      }
    }
    return sanitized;
  }

  private static boolean isUnreserved(char c) {
    return c == '-' || c == '.' || c == '_' || c == '~' || isAsciiLetterOrDigit(c);
  }

  private static boolean isAsciiLetterOrDigit(char c) {
    return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || isAsciiDigit(c);
  }

  private static boolean isAsciiHexDigit(char c) {
    return (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F') || isAsciiDigit(c);
  }

  private static boolean isAsciiDigit(char c) {
    return c >= '0' && c <= '9';
  }

  private static class Endpoint {

    private final String host;
    @Nullable private final Integer port;
    private final boolean defaultPort;

    private Endpoint(String host, @Nullable Integer port, boolean defaultPort) {
      this.host = host;
      this.port = port;
      this.defaultPort = defaultPort;
    }
  }
}
