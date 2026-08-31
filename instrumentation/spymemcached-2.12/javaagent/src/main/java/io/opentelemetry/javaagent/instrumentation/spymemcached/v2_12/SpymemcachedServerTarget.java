/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spymemcached.v2_12;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

public class SpymemcachedServerTarget {

  private static final int DEFAULT_PORT = 11211;

  private final String address;
  @Nullable private final Integer port;

  @Nullable
  public static SpymemcachedServerTarget create(@Nullable List<InetSocketAddress> nodes) {
    if (nodes == null || nodes.isEmpty()) {
      return null;
    }
    List<String> hosts = new ArrayList<>(nodes.size());
    List<Integer> ports = new ArrayList<>(nodes.size());
    int commonPort = -1;
    boolean hasMixedPorts = false;
    for (InetSocketAddress node : nodes) {
      String host = node == null ? null : clean(node.getHostString());
      if (host == null || node.getPort() <= 0) {
        return null;
      }
      if (commonPort == -1) {
        commonPort = node.getPort();
      } else if (commonPort != node.getPort()) {
        hasMixedPorts = true;
      }
      hosts.add(host);
      ports.add(node.getPort());
    }

    if (hasMixedPorts) {
      List<String> endpoints = new ArrayList<>(hosts.size());
      for (int i = 0; i < hosts.size(); i++) {
        endpoints.add(endpoint(hosts.get(i), ports.get(i)));
      }
      return new SpymemcachedServerTarget(String.join(",", endpoints), null);
    }
    return new SpymemcachedServerTarget(
        String.join(",", hosts), commonPort == DEFAULT_PORT ? null : commonPort);
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

  @Nullable
  private static String clean(@Nullable String host) {
    if (host == null) {
      return null;
    }
    String cleaned = host.trim();
    if (cleaned.startsWith("[") && cleaned.endsWith("]")) {
      cleaned = cleaned.substring(1, cleaned.length() - 1).trim();
    }
    return cleaned.isEmpty() ? null : cleaned;
  }
}
