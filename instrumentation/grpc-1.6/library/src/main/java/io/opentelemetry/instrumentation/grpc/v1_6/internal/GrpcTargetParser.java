/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_6.internal;

import java.net.URI;
import java.net.URISyntaxException;
import javax.annotation.Nullable;

/**
 * Parses gRPC target strings into server address and port per the <a
 * href="https://grpc.github.io/grpc/core/md_doc_naming.html">gRPC Name Resolution spec</a> and <a
 * href="https://github.com/open-telemetry/semantic-conventions/pull/3317">semantic conventions</a>.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public class GrpcTargetParser {

  @Nullable
  public static ParsedTarget parse(@Nullable String target) {
    if (target == null || target.isEmpty()) {
      return null;
    }

    int schemeEnd = target.indexOf("://");
    if (schemeEnd == -1) {
      // Bracketed IPv6 like "[::1]" or "[::1]:8080"
      if (target.startsWith("[")) {
        return parseHostPort(target);
      }
      // Check for single-colon scheme like "dns:endpoint" or "unix:/path"
      int colonIndex = target.indexOf(':');
      if (colonIndex == -1) {
        // No scheme, no port — just a host name
        return new ParsedTarget(target, null);
      }

      String potentialScheme = target.substring(0, colonIndex);
      if (isKnownScheme(potentialScheme)) {
        return parseSingleColonScheme(potentialScheme, target.substring(colonIndex + 1));
      }

      // No known scheme — treat as "host:port"
      return parseHostPort(target);
    }

    String scheme = target.substring(0, schemeEnd);

    if ("dns".equals(scheme)) {
      return parseDnsScheme(target);
    }

    if ("unix".equals(scheme) || "unix-abstract".equals(scheme)) {
      return parseUnixScheme(target);
    }

    if ("directaddress".equals(scheme)) {
      return null;
    }

    // Unknown scheme with "://" — use full target string as address, no port
    return new ParsedTarget(target, null);
  }

  /**
   * Parses an HTTP/2 authority of the form {@code host}, {@code host:port}, or {@code [ipv6]:port}
   * into address and port. Returns {@code null} for {@code null}/empty input.
   */
  @Nullable
  public static ParsedTarget parseAuthority(@Nullable String authority) {
    if (authority == null || authority.isEmpty()) {
      return null;
    }
    return parseHostPort(authority);
  }

  @Nullable
  private static ParsedTarget parseSingleColonScheme(String scheme, String rest) {
    if ("dns".equals(scheme)) {
      return parseDnsScheme(scheme + ":" + rest);
    }

    if ("unix".equals(scheme) || "unix-abstract".equals(scheme)) {
      return parseUnixScheme(scheme + ":" + rest);
    }

    // ipv4:, ipv6:, or other — full target as address
    return new ParsedTarget(scheme + ":" + rest, null);
  }

  @Nullable
  private static ParsedTarget parseDnsScheme(String target) {
    try {
      URI uri = new URI(target);
      String endpoint = uri.isOpaque() ? uri.getSchemeSpecificPart() : uri.getPath();
      if (endpoint == null) {
        return null;
      }
      if (endpoint.startsWith("/")) {
        endpoint = endpoint.substring(1);
      }
      return parseHostPort(endpoint);
    } catch (URISyntaxException ignored) {
      String rest = target.substring("dns:".length());
      if (rest.isEmpty() || "//".equals(rest)) {
        return null;
      }
      return new ParsedTarget(target, null);
    }
  }

  @Nullable
  private static ParsedTarget parseUnixScheme(String target) {
    try {
      URI uri = new URI(target);
      String endpoint = uri.isOpaque() ? uri.getSchemeSpecificPart() : uri.getPath();
      return endpoint == null || endpoint.isEmpty() ? null : new ParsedTarget(endpoint, null);
    } catch (URISyntaxException ignored) {
      String rest = target.substring(target.indexOf(':') + 1);
      if (rest.startsWith("//")) {
        rest = rest.substring(2);
      }
      // unix://authority/path — the path (after authority) is the address
      int slashIndex = rest.indexOf('/');
      String endpoint = slashIndex != -1 ? rest.substring(slashIndex) : rest;
      return endpoint.isEmpty() ? null : new ParsedTarget(endpoint, null);
    }
  }

  @Nullable
  private static ParsedTarget parseHostPort(String hostPort) {
    if (hostPort.isEmpty()) {
      return null;
    }

    // Handle IPv6 in brackets: [::1]:8080
    if (hostPort.startsWith("[")) {
      int closeBracket = hostPort.indexOf(']');
      if (closeBracket != -1) {
        String host = hostPort.substring(1, closeBracket);
        if (host.isEmpty()) {
          return null;
        }
        if (closeBracket + 1 < hostPort.length() && hostPort.charAt(closeBracket + 1) == ':') {
          Integer port = parsePort(hostPort.substring(closeBracket + 2));
          return new ParsedTarget(host, port);
        }
        return new ParsedTarget(host, null);
      }
    }

    int lastColon = hostPort.lastIndexOf(':');
    if (lastColon == -1) {
      return new ParsedTarget(hostPort, null);
    }

    // Multiple colons — likely bare IPv6, use as-is
    int firstColon = hostPort.indexOf(':');
    if (firstColon != lastColon) {
      return new ParsedTarget(hostPort, null);
    }

    String host = hostPort.substring(0, lastColon);
    if (host.isEmpty()) {
      return null;
    }
    Integer port = parsePort(hostPort.substring(lastColon + 1));
    return new ParsedTarget(host, port);
  }

  @Nullable
  private static Integer parsePort(String portStr) {
    try {
      int port = Integer.parseInt(portStr);
      if (port >= 0 && port <= 65535) {
        return port;
      }
    } catch (NumberFormatException ignored) {
      // ignore
    }
    return null;
  }

  private static boolean isKnownScheme(String scheme) {
    return "dns".equals(scheme)
        || "unix".equals(scheme)
        || "unix-abstract".equals(scheme)
        || "ipv4".equals(scheme)
        || "ipv6".equals(scheme)
        || "xds".equals(scheme);
  }

  private GrpcTargetParser() {}
}
