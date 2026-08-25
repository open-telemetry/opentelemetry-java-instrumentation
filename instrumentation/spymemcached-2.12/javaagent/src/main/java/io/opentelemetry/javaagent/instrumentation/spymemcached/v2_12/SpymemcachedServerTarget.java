/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spymemcached.v2_12;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/**
 * The target a Spymemcached client was configured with, rendered once while its connection is being
 * created.
 *
 * <p>A client configured with a single node keeps that node's host and its port. A client
 * configured with several carries all of them in the address, as {@code host:port,host:port}, and
 * has no port of its own. That is the syntax {@code AddrUtil} reads a node list back out of, so the
 * address stays the one an operator wrote down.
 *
 * <p>The address is rendered while the connection is being created, so a client keeps reporting the
 * nodes it was pointed at rather than the one that happens to answer an operation.
 */
public final class SpymemcachedServerTarget {

  private final String address;
  @Nullable private final Integer port;

  /**
   * The target of a client configured with {@code nodes}, or {@code null} when they name no server.
   *
   * <p>The nodes are rendered here and then forgotten, so a caller is free to keep changing the
   * list it handed over.
   */
  @Nullable
  public static SpymemcachedServerTarget create(@Nullable List<InetSocketAddress> nodes) {
    if (nodes == null || nodes.isEmpty()) {
      return null;
    }
    List<String> hosts = new ArrayList<>(nodes.size());
    List<Integer> ports = new ArrayList<>(nodes.size());
    for (InetSocketAddress node : nodes) {
      String host = node == null ? null : clean(node.getHostString());
      // a node that cannot be named drops the whole target, because a partial list describes a
      // deployment the client was never pointed at
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

  /**
   * The port of a single configured node, or {@code null} when the target names several, which
   * already carry a port each.
   */
  @Nullable
  public Integer getPort() {
    return port;
  }

  private static void appendNode(StringBuilder address, String host, int port) {
    // a literal ipv6 address is bracketed so that the port stays unambiguous
    if (host.indexOf(':') >= 0) {
      address.append('[').append(host).append(']');
    } else {
      address.append(host);
    }
    address.append(':').append(port);
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
