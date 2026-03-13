/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_6.internal;

import javax.annotation.Nullable;

/**
 * Parses gRPC target strings into server address and port per the <a
 * href="https://grpc.github.io/grpc/core/md_doc_naming.html">gRPC Name Resolution spec</a> and <a
 * href="https://github.com/open-telemetry/semantic-conventions/pull/3317">semantic conventions</a>.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class GrpcTargetParser {

  private GrpcTargetParser() {}

  @Nullable
  public static ParsedTarget parse(@Nullable String target) {
    if (target == null || target.isEmpty()) {
      return null;
    }

    int schemeEnd = target.indexOf("://");
    if (schemeEnd == -1) {
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
    String rest = target.substring(schemeEnd + 3); // after "://"

    if ("dns".equals(scheme)) {
      return parseDnsScheme(rest);
    }

    if ("unix".equals(scheme) || "unix-abstract".equals(scheme)) {
      // unix://authority/path — the path (after authority) is the address
      int slashIndex = rest.indexOf('/');
      if (slashIndex != -1) {
        return new ParsedTarget(rest.substring(slashIndex), null);
      }
      return new ParsedTarget(rest, null);
    }

    // Unknown scheme with "://" — use full target string as address, no port
    return new ParsedTarget(target, null);
  }

  private static ParsedTarget parseSingleColonScheme(String scheme, String rest) {
    if ("dns".equals(scheme)) {
      return parseHostPort(rest);
    }

    if ("unix".equals(scheme) || "unix-abstract".equals(scheme)) {
      return new ParsedTarget(rest, null);
    }

    // ipv4:, ipv6:, or other — full target as address
    return new ParsedTarget(scheme + ":" + rest, null);
  }

  private static ParsedTarget parseDnsScheme(String rest) {
    int slashIndex = rest.indexOf('/');
    String endpoint;
    if (slashIndex != -1) {
      endpoint = rest.substring(slashIndex + 1);
    } else {
      endpoint = rest;
    }
    return parseHostPort(endpoint);
  }

  private static ParsedTarget parseHostPort(String hostPort) {
    if (hostPort.isEmpty()) {
      return new ParsedTarget(hostPort, null);
    }

    // Handle IPv6 in brackets: [::1]:8080
    if (hostPort.startsWith("[")) {
      int closeBracket = hostPort.indexOf(']');
      if (closeBracket != -1) {
        String host = hostPort.substring(1, closeBracket);
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
    Integer port = parsePort(hostPort.substring(lastColon + 1));
    if (port != null) {
      return new ParsedTarget(host, port);
    }
    return new ParsedTarget(hostPort, null);
  }

  @Nullable
  private static Integer parsePort(String portStr) {
    try {
      int port = Integer.parseInt(portStr);
      if (port >= 0 && port <= 65535) {
        return port;
      }
    } catch (NumberFormatException e) {
      // ignore
    }
    return null;
  }

  private static boolean isKnownScheme(String scheme) {
    return "dns".equals(scheme)
        || "unix".equals(scheme)
        || "unix-abstract".equals(scheme)
        || "ipv4".equals(scheme)
        || "ipv6".equals(scheme);
  }
}
