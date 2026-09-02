/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.clickhouse.clientv2.v0_8;

import com.clickhouse.client.api.Client;
import com.clickhouse.client.api.ServerException;
import io.opentelemetry.instrumentation.api.incubator.semconv.net.internal.UrlParser;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.instrumentation.clickhouse.client.common.v0_5.ClickHouseDbRequest;
import io.opentelemetry.javaagent.instrumentation.clickhouse.client.common.v0_5.ClickHouseInstrumenterFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;

public class ClickHouseClientV2Singletons {

  private static final String INSTRUMENTER_NAME = "io.opentelemetry.clickhouse-client-v2-0.8";
  private static final Instrumenter<ClickHouseDbRequest, Void> instrumenter;
  private static final VirtualField<Client, ServerInfo> SERVER_INFO_FIELD =
      VirtualField.find(Client.class, ServerInfo.class);
  private static final VirtualField<Client, CurrentServerInfo> CURRENT_SERVER_INFO_FIELD =
      VirtualField.find(Client.class, CurrentServerInfo.class);

  static {
    instrumenter =
        ClickHouseInstrumenterFactory.createInstrumenter(
            INSTRUMENTER_NAME,
            error -> {
              if (error instanceof ServerException) {
                int errorCode = ((ServerException) error).getCode();
                return errorCode == 0 ? null : Integer.toString(errorCode);
              }
              return null;
            });
  }

  public static Instrumenter<ClickHouseDbRequest, Void> instrumenter() {
    return instrumenter;
  }

  public static void captureServerInfo(Client client) {
    SERVER_INFO_FIELD.set(client, ServerInfo.of(client.getEndpoints()));
  }

  // ClickHouseClientV2Test calls this reflectively to simulate a client whose endpoints were never
  // captured
  static void clearServerInfo(Client client) {
    SERVER_INFO_FIELD.set(client, null);
  }

  @Nullable
  public static ServerInfo serverInfo(Client client) {
    return SERVER_INFO_FIELD.get(client);
  }

  public static ServerInfo currentServerInfo(Client client) {
    CurrentServerInfo currentServerInfo = CURRENT_SERVER_INFO_FIELD.get(client);
    if (currentServerInfo == null) {
      currentServerInfo =
          new CurrentServerInfo(ServerInfo.ofCurrentEndpoint(client.getEndpoints()));
      CURRENT_SERVER_INFO_FIELD.set(client, currentServerInfo);
    }
    return currentServerInfo.serverInfo;
  }

  public static void capturePeer(ClickHouseDbRequest request, Object selectedNode)
      throws Exception {
    Class<?> selectedNodeClass = selectedNode.getClass();
    String host = (String) selectedNodeClass.getMethod("getHost").invoke(selectedNode);
    int port = (Integer) selectedNodeClass.getMethod("getPort").invoke(selectedNode);
    EndpointTarget endpoint = ServerInfo.sanitizeEndpoint(host);
    request.setPeer(endpoint == null ? null : endpoint.address, endpoint == null ? null : port);
  }

  public static class ServerInfo {

    private static final int MAX_ENDPOINTS = 5;
    private static final ServerInfo EMPTY = new ServerInfo(null, null, null);

    @Nullable private final String address;
    @Nullable private final Integer port;
    @Nullable private final String addressGroup;
    @Nullable private final String peerAddress;
    @Nullable private final Integer peerPort;

    private ServerInfo(
        @Nullable String address, @Nullable Integer port, @Nullable String addressGroup) {
      this(address, port, addressGroup, address, port);
    }

    private ServerInfo(
        @Nullable String address,
        @Nullable Integer port,
        @Nullable String addressGroup,
        @Nullable String peerAddress,
        @Nullable Integer peerPort) {
      this.address = address;
      this.port = port;
      this.addressGroup = addressGroup;
      this.peerAddress = peerAddress;
      this.peerPort = peerPort;
    }

    public static ServerInfo empty() {
      return EMPTY;
    }

    static ServerInfo of(Set<String> endpoints) {
      if (endpoints.isEmpty()) {
        return EMPTY;
      }
      if (endpoints.size() == 1) {
        EndpointTarget endpoint = sanitizeEndpoint(endpoints.iterator().next());
        if (endpoint == null) {
          return EMPTY;
        }
        return new ServerInfo(
            endpoint.address, endpoint.isDefaultPort() ? null : endpoint.port, null);
      }

      // Endpoint iteration order is unspecified, so canonicalize the configured target.
      List<EndpointTarget> sanitized = new ArrayList<>(endpoints.size());
      boolean hasNonDefaultPort = false;
      for (String endpoint : endpoints) {
        EndpointTarget sanitizedEndpoint = sanitizeEndpoint(endpoint);
        if (sanitizedEndpoint == null) {
          return EMPTY;
        }
        sanitized.add(sanitizedEndpoint);
        hasNonDefaultPort |= !sanitizedEndpoint.isDefaultPort();
      }
      boolean inlinePorts = hasNonDefaultPort;
      sanitized.sort(
          (left, right) -> left.render(inlinePorts).compareTo(right.render(inlinePorts)));

      StringBuilder addressGroup = new StringBuilder();
      for (int i = 0; i < Math.min(sanitized.size(), MAX_ENDPOINTS); i++) {
        if (addressGroup.length() > 0) {
          addressGroup.append(',');
        }
        addressGroup.append(sanitized.get(i).render(inlinePorts));
      }
      return new ServerInfo(null, null, addressGroup.toString());
    }

    public static ServerInfo ofCurrentEndpoint(Set<String> endpoints) {
      if (endpoints.isEmpty()) {
        return EMPTY;
      }
      String endpoint = endpoints.iterator().next();
      EndpointTarget peer = sanitizeEndpoint(endpoint);
      return new ServerInfo(
          UrlParser.getHost(endpoint),
          UrlParser.getPort(endpoint),
          null,
          peer == null ? null : peer.address,
          peer == null ? null : peer.port);
    }

    private static String endpointAddress(String endpoint) {
      if (endpoint.startsWith("[")) {
        int bracketEnd = endpoint.indexOf(']');
        if (bracketEnd > 0) {
          return endpoint.substring(1, bracketEnd);
        }
      }
      int colon = endpoint.lastIndexOf(':');
      return colon >= 0 && colon == endpoint.indexOf(':') ? endpoint.substring(0, colon) : endpoint;
    }

    @Nullable
    private static Integer endpointPort(String endpoint) {
      int portStart;
      if (endpoint.startsWith("[")) {
        int bracketEnd = endpoint.indexOf(']');
        portStart =
            bracketEnd >= 0
                    && bracketEnd + 1 < endpoint.length()
                    && endpoint.charAt(bracketEnd + 1) == ':'
                ? bracketEnd + 2
                : -1;
      } else {
        int colon = endpoint.lastIndexOf(':');
        portStart = colon >= 0 && colon == endpoint.indexOf(':') ? colon + 1 : -1;
      }
      if (portStart < 0 || portStart == endpoint.length()) {
        return null;
      }
      try {
        return Integer.valueOf(endpoint.substring(portStart));
      } catch (NumberFormatException ignored) {
        return null;
      }
    }

    @Nullable
    private static EndpointTarget sanitizeEndpoint(String endpoint) {
      String scheme = null;
      int authorityStart = endpoint.indexOf("://");
      if (authorityStart < 0) {
        authorityStart = 0;
      } else {
        scheme = endpoint.substring(0, authorityStart);
        authorityStart += 3;
      }
      int authorityEnd = endpoint.length();
      for (int i = authorityStart; i < endpoint.length(); i++) {
        char c = endpoint.charAt(i);
        if (c == '/' || c == '?' || c == '#') {
          authorityEnd = i;
          break;
        }
      }
      int userInfoEnd = endpoint.lastIndexOf('@', authorityEnd - 1);
      if (userInfoEnd >= authorityStart) {
        authorityStart = userInfoEnd + 1;
      }
      String authority = endpoint.substring(authorityStart, authorityEnd);
      if (authority.indexOf('=') >= 0 || hasUnsafePercentEscape(authority)) {
        return null;
      }
      String address = endpointAddress(authority);
      if (address.isEmpty()) {
        return null;
      }
      return new EndpointTarget(scheme, address, endpointPort(authority));
    }

    private static boolean hasUnsafePercentEscape(String authority) {
      int percent = authority.indexOf('%');
      if (percent < 0) {
        return false;
      }
      int bracketEnd = authority.indexOf(']');
      return !authority.startsWith("[")
          || bracketEnd < percent
          || authority.indexOf('%', percent + 1) >= 0;
    }

    @Nullable
    public String getAddress() {
      return address;
    }

    @Nullable
    public Integer getPort() {
      return port;
    }

    @Nullable
    public String getAddressGroup() {
      return addressGroup;
    }

    @Nullable
    public String getPeerAddress() {
      return peerAddress;
    }

    @Nullable
    public Integer getPeerPort() {
      return peerPort;
    }
  }

  private static class EndpointTarget {
    @Nullable private final String scheme;
    private final String address;
    @Nullable private final Integer port;

    private EndpointTarget(@Nullable String scheme, String address, @Nullable Integer port) {
      this.scheme = scheme;
      this.address = address;
      this.port = port;
    }

    private boolean isDefaultPort() {
      Integer defaultPort = defaultPort();
      return defaultPort != null && (port == null || defaultPort.equals(port));
    }

    @Nullable
    private Integer defaultPort() {
      if ("http".equalsIgnoreCase(scheme)) {
        return 8123;
      }
      if ("https".equalsIgnoreCase(scheme)) {
        return 8443;
      }
      return null;
    }

    private String render(boolean includePort) {
      StringBuilder rendered = new StringBuilder();
      if (address.indexOf(':') >= 0) {
        rendered.append('[').append(address).append(']');
      } else {
        rendered.append(address);
      }
      Integer renderedPort = port != null ? port : defaultPort();
      if (includePort && renderedPort != null) {
        rendered.append(':').append(renderedPort);
      }
      return rendered.toString();
    }
  }

  // VirtualField keys its storage by the owner class and the field type, so this wrapper is what
  // keeps CURRENT_SERVER_INFO_FIELD separate from SERVER_INFO_FIELD.
  private static class CurrentServerInfo {
    private final ServerInfo serverInfo;

    private CurrentServerInfo(ServerInfo serverInfo) {
      this.serverInfo = serverInfo;
    }
  }

  private ClickHouseClientV2Singletons() {}
}
