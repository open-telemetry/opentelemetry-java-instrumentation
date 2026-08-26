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

  private final String address;
  @Nullable private final Integer port;

  @Nullable
  public static SpymemcachedServerTarget create(@Nullable List<InetSocketAddress> nodes) {
    if (nodes == null || nodes.isEmpty()) {
      return null;
    }
    List<String> hosts = new ArrayList<>(nodes.size());
    List<Integer> ports = new ArrayList<>(nodes.size());
    for (InetSocketAddress node : nodes) {
      String host = node == null ? null : clean(node.getHostString());
      if (host == null || node.getPort() <= 0) {
        return null;
      }
      hosts.add(host);
      ports.add(node.getPort());
    }
    if (hosts.size() == 1) {
      return new SpymemcachedServerTarget(hosts.get(0), ports.get(0));
    }
    StringBuilder address = new StringBuilder();
    for (int i = 0; i < hosts.size(); i++) {
      if (i > 0) {
        address.append(',');
      }
      appendNode(address, hosts.get(i), ports.get(i));
    }
    return new SpymemcachedServerTarget(address.toString(), null);
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

  private static void appendNode(StringBuilder address, String host, int port) {
    if (host.indexOf(':') >= 0) {
      address.append('[').append(host).append(']');
    } else {
      address.append(host);
    }
    address.append(':').append(port);
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
