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
 * The target a Geode client pool was configured with, read once while the pool is being created.
 *
 * <p>A pool configured with one explicit cache server keeps that server's host and its port. A pool
 * configured with one locator is named by that locator's host, which carries no port of its own,
 * because a locator says where to look for cache servers rather than which one answers. Explicit
 * servers take precedence over locator discovery.
 *
 * <p>Several servers, or several locators, do not form one server address, so a pool configured
 * that way has no target and the operations it carries name no server.
 *
 * <p>The target is read while the pool is being created, so a pool keeps reporting what it was
 * pointed at rather than the servers it later discovers.
 */
class GeodeServerTarget {

  private final String address;
  @Nullable private final Integer port;

  static Builder builder() {
    return new Builder();
  }

  private GeodeServerTarget(String address, @Nullable Integer port) {
    this.address = address;
    this.port = port;
  }

  String getAddress() {
    return address;
  }

  /**
   * The port of the single explicitly configured cache server, or {@code null} when the target
   * names a locator.
   */
  @Nullable
  Integer getPort() {
    return port;
  }

  /** Collects the servers and locators a pool is being configured with. */
  static class Builder {

    private static final int MAX_PORT = 65535;

    private final SortedMap<String, Endpoint> servers = new TreeMap<>();
    private final SortedMap<String, Endpoint> locators = new TreeMap<>();
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
    synchronized void addServer(@Nullable String host, int port) {
      serverConfigured = true;
      serversComplete &= add(servers, host, port);
    }

    /**
     * Adds a configured locator.
     *
     * <p>A locator that cannot be named drops the whole locator list, because a partial list
     * describes a discovery target the client was never pointed at.
     */
    synchronized void addLocator(@Nullable String host, int port) {
      locatorConfigured = true;
      locatorsComplete &= add(locators, host, port);
    }

    /** Forgets everything configured so far, as {@code PoolFactory.reset()} does. */
    synchronized void reset() {
      servers.clear();
      locators.clear();
      serverConfigured = false;
      locatorConfigured = false;
      serversComplete = true;
      locatorsComplete = true;
    }

    /**
     * The target configured so far, or {@code null} when it names neither one cache server nor one
     * locator.
     */
    @Nullable
    synchronized GeodeServerTarget build() {
      if (serverConfigured) {
        return buildServer();
      }
      if (locatorConfigured) {
        return buildLocator();
      }
      return null;
    }

    @Nullable
    private GeodeServerTarget buildServer() {
      if (!serversComplete || servers.size() != 1) {
        return null;
      }
      Endpoint only = servers.get(servers.firstKey());
      return new GeodeServerTarget(only.host, only.port);
    }

    @Nullable
    private GeodeServerTarget buildLocator() {
      if (!locatorsComplete || locators.size() != 1) {
        return null;
      }
      return new GeodeServerTarget(locators.get(locators.firstKey()).host, null);
    }

    private static boolean add(Map<String, Endpoint> endpoints, @Nullable String host, int port) {
      String cleaned = clean(host);
      if (cleaned == null || port <= 0 || port > MAX_PORT) {
        return false;
      }
      Endpoint endpoint = new Endpoint(cleaned, port);
      endpoints.put(endpoint.key(), endpoint);
      return true;
    }

    /**
     * The key that tells two configured endpoints apart, so the same one added twice counts once.
     */
    private static String key(String host, int port) {
      StringBuilder key = new StringBuilder();
      // a literal ipv6 address is bracketed so that the port stays unambiguous
      if (host.indexOf(':') >= 0) {
        key.append('[').append(host).append(']');
      } else {
        key.append(host);
      }
      key.append(':').append(port);
      return key.toString();
    }

    /**
     * The bare host of a configured endpoint, or {@code null} when it names none.
     *
     * <p>A host is reported unbracketed, where brackets would only get in the way of matching it
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

  private static class Endpoint {

    private final String host;
    private final int port;

    private Endpoint(String host, int port) {
      this.host = host;
      this.port = port;
    }

    private String key() {
      return Builder.key(host, port);
    }
  }
}
