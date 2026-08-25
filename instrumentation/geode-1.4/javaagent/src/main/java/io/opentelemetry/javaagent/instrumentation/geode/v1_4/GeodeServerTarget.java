/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.geode.v1_4;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/**
 * The target a Geode client pool was configured with, rendered once while the pool is being
 * created.
 *
 * <p>A pool configured with a server group is named by that group, a logical name that carries no
 * port. A pool configured with a single cache server keeps that server's host and its port. A pool
 * configured with several carries all of them in the address, as {@code host:port,host:port}, and
 * has no port of its own.
 *
 * <p>Locators are never part of the target. A locator hands a client the cache servers it may talk
 * to, so its address names a directory rather than a server an operation reaches. A pool given
 * locators and no server group therefore has no target at all.
 *
 * <p>The address is rendered while the pool is being created, so a pool keeps reporting what it was
 * pointed at rather than the servers it later discovers.
 */
public final class GeodeServerTarget {

  private final String address;
  @Nullable private final Integer port;

  public static Builder builder() {
    return new Builder();
  }

  private GeodeServerTarget(String address, @Nullable Integer port) {
    this.address = address;
    this.port = port;
  }

  public String getAddress() {
    return address;
  }

  /**
   * The port of a single configured cache server, or {@code null} when the target names a server
   * group or several servers.
   */
  @Nullable
  public Integer getPort() {
    return port;
  }

  /** Collects the cache servers and the server group a pool is being configured with. */
  public static final class Builder {

    private static final int MAX_PORT = 65535;

    private final List<String> hosts = new ArrayList<>();
    private final List<Integer> ports = new ArrayList<>();
    @Nullable private String serverGroup;
    private boolean complete = true;

    private Builder() {}

    /**
     * Adds a configured cache server.
     *
     * <p>A server that cannot be named drops the whole server list, because a partial list
     * describes a deployment the client was never pointed at.
     */
    public synchronized void addServer(@Nullable String host, int port) {
      String cleaned = clean(host);
      if (cleaned == null || port <= 0 || port > MAX_PORT) {
        complete = false;
        return;
      }
      hosts.add(cleaned);
      ports.add(port);
    }

    public synchronized void setServerGroup(@Nullable String serverGroup) {
      this.serverGroup = serverGroup;
    }

    /** Forgets everything configured so far, as {@code PoolFactory.reset()} does. */
    public synchronized void reset() {
      hosts.clear();
      ports.clear();
      serverGroup = null;
      complete = true;
    }

    /**
     * The target configured so far, or {@code null} when it names neither a server group nor a
     * server.
     *
     * <p>A configured server group wins over the server list. It is the logical group of servers an
     * operator pointed the client at, while the servers are only the way a client reaches that
     * group.
     */
    @Nullable
    public synchronized GeodeServerTarget build() {
      String group = serverGroup == null ? "" : serverGroup.trim();
      if (!group.isEmpty()) {
        return new GeodeServerTarget(group, null);
      }
      if (!complete || hosts.isEmpty()) {
        return null;
      }
      if (hosts.size() == 1) {
        return new GeodeServerTarget(hosts.get(0), ports.get(0));
      }
      StringBuilder address = new StringBuilder();
      for (int i = 0; i < hosts.size(); i++) {
        if (i > 0) {
          address.append(',');
        }
        appendServer(address, hosts.get(i), ports.get(i));
      }
      return new GeodeServerTarget(address.toString(), null);
    }

    private static void appendServer(StringBuilder address, String host, int port) {
      // a literal ipv6 address is bracketed so that the port stays unambiguous
      if (host.indexOf(':') >= 0) {
        address.append('[').append(host).append(']');
      } else {
        address.append(host);
      }
      address.append(':').append(port);
    }

    /**
     * The bare host of a configured server, or {@code null} when it names none.
     *
     * <p>A lone host is reported unbracketed, where brackets would only get in the way of matching
     * it against a configured peer.
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
}
