/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.geode.v1_4;

import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import javax.annotation.Nullable;

/**
 * The target a Geode client pool was configured with, rendered once while the pool is being
 * created.
 *
 * <p>A pool configured with an explicit cache server keeps that server's host and its port. A pool
 * configured with several carries all of them in the address, as {@code host:port,host:port}, and
 * has no port of its own. Explicit servers take precedence over locator discovery and its server
 * group.
 *
 * <p>A locator-backed pool carries each configured locator in the address. When it selects a server
 * group, every comma-separated locator is independently scoped as {@code host:port/group}.
 *
 * <p>The address is rendered while the pool is being created, so a pool keeps reporting what it was
 * pointed at rather than the servers it later discovers.
 */
public class GeodeServerTarget {

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
   * The port of a single explicitly configured cache server, or {@code null} when the target names
   * locator discovery, a server group, or several servers.
   */
  @Nullable
  public Integer getPort() {
    return port;
  }

  /** Collects the servers, locators, and server group a pool is being configured with. */
  public static class Builder {

    private static final int MAX_PORT = 65535;

    private final SortedMap<String, Endpoint> servers = new TreeMap<>();
    private final SortedMap<String, Endpoint> locators = new TreeMap<>();
    @Nullable private String serverGroup;
    private boolean serverConfigured;
    private boolean locatorConfigured;
    private boolean serversComplete = true;
    private boolean locatorsComplete = true;

    private Builder() {}

    /**
     * Adds a configured cache server.
     *
     * <p>A server that cannot be named drops the whole server list, because a partial list
     * describes a deployment the client was never pointed at.
     */
    public synchronized void addServer(@Nullable String host, int port) {
      serverConfigured = true;
      serversComplete &= add(servers, host, port);
    }

    /**
     * Adds a configured locator.
     *
     * <p>A locator that cannot be named drops the whole locator list, because a partial list
     * describes a discovery target the client was never pointed at.
     */
    public synchronized void addLocator(@Nullable String host, int port) {
      locatorConfigured = true;
      locatorsComplete &= add(locators, host, port);
    }

    public synchronized void setServerGroup(@Nullable String serverGroup) {
      this.serverGroup = serverGroup;
    }

    /** Forgets everything configured so far, as {@code PoolFactory.reset()} does. */
    public synchronized void reset() {
      servers.clear();
      locators.clear();
      serverGroup = null;
      serverConfigured = false;
      locatorConfigured = false;
      serversComplete = true;
      locatorsComplete = true;
    }

    /**
     * The target configured so far, or {@code null} when it names no explicit server, locator, or
     * server group.
     */
    @Nullable
    public synchronized GeodeServerTarget build() {
      if (serverConfigured) {
        return buildServers();
      }
      String group = serverGroup == null ? "" : serverGroup.trim();
      if (locatorConfigured) {
        return buildLocators(group);
      }
      return group.isEmpty() ? null : new GeodeServerTarget(group, null);
    }

    @Nullable
    private GeodeServerTarget buildServers() {
      if (!serversComplete || servers.isEmpty()) {
        return null;
      }
      if (servers.size() == 1) {
        Endpoint only = servers.get(servers.firstKey());
        return new GeodeServerTarget(only.host, only.port);
      }
      return new GeodeServerTarget(String.join(",", servers.keySet()), null);
    }

    @Nullable
    private GeodeServerTarget buildLocators(String group) {
      if (!locatorsComplete || locators.isEmpty()) {
        return null;
      }
      StringBuilder address = new StringBuilder();
      for (String locator : locators.keySet()) {
        if (address.length() > 0) {
          address.append(',');
        }
        address.append(locator);
        if (!group.isEmpty()) {
          address.append('/').append(group);
        }
      }
      return new GeodeServerTarget(address.toString(), null);
    }

    private static boolean add(Map<String, Endpoint> endpoints, @Nullable String host, int port) {
      String cleaned = clean(host);
      if (cleaned == null || port <= 0 || port > MAX_PORT) {
        return false;
      }
      Endpoint endpoint = new Endpoint(cleaned, port);
      endpoints.put(endpoint.render(), endpoint);
      return true;
    }

    private static String render(String host, int port) {
      StringBuilder address = new StringBuilder();
      // a literal ipv6 address is bracketed so that the port stays unambiguous
      if (host.indexOf(':') >= 0) {
        address.append('[').append(host).append(']');
      } else {
        address.append(host);
      }
      address.append(':').append(port);
      return address.toString();
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

  private static class Endpoint {

    private final String host;
    private final int port;

    private Endpoint(String host, int port) {
      this.host = host;
      this.port = port;
    }

    private String render() {
      return Builder.render(host, port);
    }
  }
}
