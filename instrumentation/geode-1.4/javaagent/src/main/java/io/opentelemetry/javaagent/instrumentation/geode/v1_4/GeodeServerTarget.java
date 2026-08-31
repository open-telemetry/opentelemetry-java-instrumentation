/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.geode.v1_4;

import java.net.InetAddress;
import java.net.UnknownHostException;
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

    private static final int DEFAULT_SERVER_PORT = 40404;
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
        return buildLocators(cleanGroup(serverGroup));
      }
      return null;
    }

    @Nullable
    private GeodeServerTarget buildServers() {
      if (!serversComplete || servers.isEmpty()) {
        return null;
      }

      int commonPort = servers.get(0).port;
      boolean portsMatch = true;
      for (int i = 1; i < servers.size(); i++) {
        if (servers.get(i).port != commonPort) {
          portsMatch = false;
          break;
        }
      }

      if (portsMatch) {
        Integer port = commonPort == DEFAULT_SERVER_PORT ? null : commonPort;
        return new GeodeServerTarget(render(servers, false), port);
      }
      return new GeodeServerTarget(render(servers, true), null);
    }

    @Nullable
    private GeodeServerTarget buildLocators(@Nullable String group) {
      if (!locatorsComplete || locators.isEmpty()) {
        return null;
      }
      String address = render(locators, true);
      if (group != null) {
        address += "/" + group;
      }
      return new GeodeServerTarget(address, null);
    }

    private static boolean add(List<Endpoint> endpoints, @Nullable String host, int port) {
      String cleaned = cleanHost(host);
      if (cleaned == null || port <= 0 || port > MAX_PORT) {
        return false;
      }
      endpoints.add(new Endpoint(cleaned, port));
      return true;
    }

    private static String render(List<Endpoint> endpoints, boolean includePort) {
      List<String> rendered = new ArrayList<>(endpoints.size());
      for (Endpoint endpoint : endpoints) {
        rendered.add(endpoint.render(includePort));
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
    private static String cleanHost(@Nullable String host) {
      if (host == null) {
        return null;
      }
      String cleaned = host.trim();
      boolean startsWithBracket = cleaned.startsWith("[");
      boolean endsWithBracket = cleaned.endsWith("]");
      if (startsWithBracket || endsWithBracket) {
        if (!startsWithBracket || !endsWithBracket || cleaned.length() <= 2) {
          return null;
        }
        cleaned = cleaned.substring(1, cleaned.length() - 1).trim();
      }
      if (cleaned.isEmpty() || cleaned.indexOf('[') >= 0 || cleaned.indexOf(']') >= 0) {
        return null;
      }
      if (cleaned.indexOf(':') >= 0) {
        return isIpv6Address(cleaned) ? cleaned : null;
      }
      if (looksLikeIpv4Address(cleaned)) {
        return isIpv4Address(cleaned) ? cleaned : null;
      }
      return isHostname(cleaned) ? cleaned : null;
    }

    private static boolean isIpv6Address(String host) {
      for (int i = 0; i < host.length(); i++) {
        char c = host.charAt(i);
        if (c != ':' && c != '.' && !isAsciiHexDigit(c)) {
          return false;
        }
      }
      try {
        InetAddress.getByName(host);
        return true;
      } catch (UnknownHostException ignored) {
        return false;
      }
    }

    private static boolean isAsciiHexDigit(char c) {
      return (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F') || (c >= '0' && c <= '9');
    }

    private static boolean looksLikeIpv4Address(String host) {
      for (int i = 0; i < host.length(); i++) {
        char c = host.charAt(i);
        if (c != '.' && (c < '0' || c > '9')) {
          return false;
        }
      }
      return host.indexOf('.') >= 0;
    }

    private static boolean isIpv4Address(String host) {
      String[] parts = host.split("\\.", -1);
      if (parts.length != 4) {
        return false;
      }
      for (String part : parts) {
        if (part.isEmpty() || part.length() > 3) {
          return false;
        }
        int value = 0;
        for (int i = 0; i < part.length(); i++) {
          value = value * 10 + part.charAt(i) - '0';
        }
        if (value > 255) {
          return false;
        }
      }
      return true;
    }

    private static boolean isHostname(String host) {
      int length = host.endsWith(".") ? host.length() - 1 : host.length();
      if (length <= 0 || length > 253) {
        return false;
      }
      String[] labels = host.substring(0, length).split("\\.", -1);
      for (String label : labels) {
        if (label.isEmpty()
            || label.length() > 63
            || !isAsciiLetterOrDigit(label.charAt(0))
            || !isAsciiLetterOrDigit(label.charAt(label.length() - 1))) {
          return false;
        }
        for (int i = 1; i < label.length() - 1; i++) {
          char c = label.charAt(i);
          if (c != '-' && !isAsciiLetterOrDigit(c)) {
            return false;
          }
        }
      }
      return true;
    }

    private static boolean isAsciiLetterOrDigit(char c) {
      return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9');
    }

    @Nullable
    private static String cleanGroup(@Nullable String group) {
      if (group == null) {
        return null;
      }
      String cleaned = group.trim();
      if (cleaned.isEmpty() || cleaned.equals(".") || cleaned.equals("..")) {
        return null;
      }
      for (int i = 0; i < cleaned.length(); i++) {
        char c = cleaned.charAt(i);
        if (c != '-' && c != '.' && c != '_' && c != '~' && !isAsciiLetterOrDigit(c)) {
          return null;
        }
      }
      return cleaned;
    }
  }

  private static class Endpoint {

    private final String host;
    private final int port;

    private Endpoint(String host, int port) {
      this.host = host;
      this.port = port;
    }

    private String render(boolean includePort) {
      return includePort ? Builder.render(host, port) : host;
    }
  }
}
