/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.geode.v1_4;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

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

  @Nullable
  Integer getPort() {
    return port;
  }

  static class Builder {

    private static final int MAX_PORT = 65535;

    private final List<Endpoint> servers = new ArrayList<>();
    private final List<Endpoint> locators = new ArrayList<>();
    @Nullable private String serverGroup;
    private boolean serverConfigured;
    private boolean locatorConfigured;
    private boolean serversComplete = true;
    private boolean locatorsComplete = true;

    private Builder() {}

    synchronized void addServer(@Nullable String host, int port) {
      serverConfigured = true;
      // A partial list would describe a deployment the client was never pointed at.
      serversComplete &= add(servers, host, port);
    }

    synchronized void addLocator(@Nullable String host, int port) {
      locatorConfigured = true;
      // A partial list would describe a discovery target the client was never pointed at.
      locatorsComplete &= add(locators, host, port);
    }

    synchronized void setServerGroup(@Nullable String serverGroup) {
      this.serverGroup = serverGroup;
    }

    synchronized void reset() {
      servers.clear();
      locators.clear();
      serverGroup = null;
      serverConfigured = false;
      locatorConfigured = false;
      serversComplete = true;
      locatorsComplete = true;
    }

    @Nullable
    synchronized GeodeServerTarget build() {
      if (serverConfigured) {
        return buildServers();
      }
      if (locatorConfigured) {
        String group = serverGroup == null ? "" : serverGroup.trim();
        return buildLocators(group);
      }
      return null;
    }

    @Nullable
    private GeodeServerTarget buildServers() {
      if (!serversComplete || servers.isEmpty()) {
        return null;
      }
      if (servers.size() == 1) {
        Endpoint only = servers.get(0);
        return new GeodeServerTarget(only.host, only.port);
      }
      return new GeodeServerTarget(render(servers), null);
    }

    @Nullable
    private GeodeServerTarget buildLocators(String group) {
      if (!locatorsComplete || locators.isEmpty()) {
        return null;
      }
      String address = render(locators);
      if (!group.isEmpty()) {
        address += "/" + group;
      }
      return new GeodeServerTarget(address, null);
    }

    private static boolean add(List<Endpoint> endpoints, @Nullable String host, int port) {
      String cleaned = clean(host);
      if (cleaned == null || port <= 0 || port > MAX_PORT) {
        return false;
      }
      endpoints.add(new Endpoint(cleaned, port));
      return true;
    }

    private static String render(List<Endpoint> endpoints) {
      List<String> rendered = new ArrayList<>(endpoints.size());
      for (Endpoint endpoint : endpoints) {
        rendered.add(endpoint.render());
      }
      rendered.sort(String::compareTo);
      return String.join(",", rendered);
    }

    private static String render(String host, int port) {
      StringBuilder address = new StringBuilder();
      // Brackets distinguish an IPv6 address from its port.
      if (host.indexOf(':') >= 0) {
        address.append('[').append(host).append(']');
      } else {
        address.append(host);
      }
      address.append(':').append(port);
      return address.toString();
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
