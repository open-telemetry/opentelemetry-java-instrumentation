/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spymemcached.v2_12;

import java.net.InetSocketAddress;
import java.util.List;
import javax.annotation.Nullable;

/**
 * The single server a Spymemcached client was configured with, captured while its connection is
 * being created.
 *
 * <p>Clients configured with several nodes have no single configured server. Their requests report
 * the node that handles each operation instead.
 */
public class SpymemcachedServerTarget {

  private final String address;
  private final int port;

  /**
   * The target of a client configured with one node, or {@code null} when the client has no single
   * configured server.
   *
   * <p>The node is copied here and then forgotten, so a caller is free to keep changing the list it
   * handed over.
   */
  @Nullable
  public static SpymemcachedServerTarget create(@Nullable List<InetSocketAddress> nodes) {
    if (nodes == null || nodes.size() != 1) {
      return null;
    }
    InetSocketAddress node = nodes.get(0);
    String host = node == null ? null : clean(node.getHostString());
    if (host == null || node.getPort() <= 0) {
      return null;
    }
    return new SpymemcachedServerTarget(host, node.getPort());
  }

  private SpymemcachedServerTarget(String address, int port) {
    this.address = address;
    this.port = port;
  }

  public String getAddress() {
    return address;
  }

  public int getPort() {
    return port;
  }

  /**
   * The bare host of a configured node, or {@code null} when it names none.
   *
   * <p>A lone host is reported unbracketed, where brackets would only get in the way of matching it
   * against a configured peer.
   */
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
