/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spymemcached.v2_12;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

public class SpymemcachedServerTarget {

  private static final int DEFAULT_PORT = 11211;
  private static final int MAX_ENDPOINT_COUNT = 5;

  private final String address;
  @Nullable private final Integer port;

  @Nullable
  public static SpymemcachedServerTarget create(@Nullable List<InetSocketAddress> nodes) {
    if (nodes == null || nodes.isEmpty()) {
      return null;
    }
    List<String> hosts = new ArrayList<>(nodes.size());
    List<Integer> ports = new ArrayList<>(nodes.size());
    boolean hasNonDefaultPort = false;
    for (InetSocketAddress node : nodes) {
      String host = node == null ? null : sanitizeHost(node.getHostString());
      if (host == null || node.getPort() <= 0) {
        return null;
      }
      hasNonDefaultPort |= node.getPort() != DEFAULT_PORT;
      hosts.add(host);
      ports.add(node.getPort());
    }

    if (nodes.size() > 1 && hasNonDefaultPort) {
      List<String> endpoints = new ArrayList<>(hosts.size());
      for (int i = 0; i < hosts.size(); i++) {
        endpoints.add(endpoint(hosts.get(i), ports.get(i)));
      }
      return new SpymemcachedServerTarget(joinEndpoints(endpoints), null);
    }
    return new SpymemcachedServerTarget(
        joinEndpoints(hosts), hasNonDefaultPort ? ports.get(0) : null);
  }

  private SpymemcachedServerTarget(String address, @Nullable Integer port) {
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

  private static String endpoint(String host, int port) {
    StringBuilder endpoint = new StringBuilder();
    if (host.indexOf(':') >= 0) {
      endpoint.append('[').append(host).append(']');
    } else {
      endpoint.append(host);
    }
    return endpoint.append(':').append(port).toString();
  }

  private static String joinEndpoints(List<String> endpoints) {
    StringBuilder address = new StringBuilder();
    int endpointCount = Math.min(endpoints.size(), MAX_ENDPOINT_COUNT);
    for (int i = 0; i < endpointCount; i++) {
      if (address.length() != 0) {
        address.append(',');
      }
      address.append(endpoints.get(i));
    }
    return address.toString();
  }

  @Nullable
  private static String sanitizeHost(@Nullable String host) {
    if (host == null) {
      return null;
    }
    String cleaned = host.trim();
    boolean bracketed = cleaned.startsWith("[") && cleaned.endsWith("]");
    if (bracketed) {
      cleaned = cleaned.substring(1, cleaned.length() - 1).trim();
    }
    if (cleaned.isEmpty() || cleaned.startsWith("[") || cleaned.endsWith("]")) {
      return null;
    }

    if (cleaned.indexOf(':') >= 0) {
      return isSafeIpv6Host(cleaned) ? cleaned : null;
    }
    if (bracketed) {
      return null;
    }
    for (int i = 0; i < cleaned.length(); i++) {
      char c = cleaned.charAt(i);
      if (!Character.isLetterOrDigit(c) && c != '-' && c != '.' && c != '_') {
        return null;
      }
    }
    return cleaned;
  }

  private static boolean isSafeIpv6Host(String host) {
    int zoneSeparator = host.indexOf('%');
    String address = zoneSeparator < 0 ? host : host.substring(0, zoneSeparator);
    if (address.isEmpty() || (zoneSeparator >= 0 && host.indexOf('%', zoneSeparator + 1) >= 0)) {
      return false;
    }
    try {
      new URI("http", null, address, -1, null, null, null);
    } catch (URISyntaxException ignored) {
      return false;
    }
    if (zoneSeparator < 0 || zoneSeparator == host.length() - 1) {
      return zoneSeparator < 0;
    }
    for (int i = zoneSeparator + 1; i < host.length(); i++) {
      char c = host.charAt(i);
      if (!Character.isLetterOrDigit(c) && c != '-' && c != '.' && c != '_' && c != '~') {
        return false;
      }
    }
    return true;
  }
}
