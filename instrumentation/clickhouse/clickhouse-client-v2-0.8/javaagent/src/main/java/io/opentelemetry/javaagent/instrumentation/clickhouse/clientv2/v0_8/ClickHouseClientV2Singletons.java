/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.clickhouse.clientv2.v0_8;

import com.clickhouse.client.api.Client;
import com.clickhouse.client.api.ServerException;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTarget;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTargetBuilder;
import io.opentelemetry.instrumentation.api.incubator.semconv.net.internal.UrlParser;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.instrumentation.clickhouse.client.common.v0_5.ClickHouseDbRequest;
import io.opentelemetry.javaagent.instrumentation.clickhouse.client.common.v0_5.ClickHouseInstrumenterFactory;
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
    DbServerTarget target = DbServerTarget.builder(port).addEndpoint(host, -1).build();
    request.setPeer(target == null ? null : target.getAddress(), target == null ? null : port);
  }

  public static class ServerInfo {

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
      DbServerTargetBuilder builder = DbServerTarget.builder(-1);
      boolean inlinePorts = false;
      for (String endpoint : endpoints) {
        EndpointTarget extracted = extractEndpoint(endpoint);
        if (extracted == null) {
          return EMPTY;
        }
        int defaultPort = extracted.defaultPort();
        builder.addEndpoint(
            extracted.address, extracted.port == null ? -1 : extracted.port, defaultPort);
        inlinePorts |= extracted.port != null && extracted.port != defaultPort;
      }
      DbServerTarget target = builder.build();
      if (target == null) {
        return EMPTY;
      }
      return endpoints.size() == 1
          ? new ServerInfo(target.getAddress(), target.getPort(), null)
          : new ServerInfo(
              null,
              null,
              inlinePorts ? target.getAddress() : bracketIpv6Endpoints(target.getAddress()));
    }

    private static ServerInfo ofCurrentEndpoint(Set<String> endpoints) {
      if (endpoints.isEmpty()) {
        return EMPTY;
      }
      String endpoint = endpoints.iterator().next();
      EndpointTarget extracted = extractEndpoint(endpoint);
      DbServerTarget peer =
          extracted == null
              ? null
              : DbServerTarget.builder(extracted.defaultPort())
                  .addEndpoint(extracted.address, extracted.port == null ? -1 : extracted.port)
                  .build();
      return new ServerInfo(
          UrlParser.getHost(endpoint),
          UrlParser.getPort(endpoint),
          null,
          peer == null ? null : peer.getAddress(),
          extracted == null ? null : extracted.port);
    }

    @Nullable
    private static EndpointTarget extractEndpoint(String endpoint) {
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
      if (authority.startsWith("[")) {
        int bracketEnd = authority.indexOf(']');
        if (bracketEnd <= 1 || authority.indexOf(']', bracketEnd + 1) >= 0) {
          return null;
        }
        String rest = authority.substring(bracketEnd + 1);
        Integer port =
            rest.isEmpty() ? null : parsePort(rest.startsWith(":") ? rest.substring(1) : "");
        return !rest.isEmpty() && port == null
            ? null
            : new EndpointTarget(scheme, authority.substring(1, bracketEnd), port);
      }
      if (authority.indexOf('[') >= 0 || authority.indexOf(']') >= 0) {
        return null;
      }
      int firstColon = authority.indexOf(':');
      int lastColon = authority.lastIndexOf(':');
      if (firstColon >= 0 && firstColon == lastColon) {
        Integer port = parsePort(authority.substring(firstColon + 1));
        return firstColon == 0 || port == null
            ? null
            : new EndpointTarget(scheme, authority.substring(0, firstColon), port);
      }
      return new EndpointTarget(scheme, authority, null);
    }

    @Nullable
    private static Integer parsePort(String value) {
      if (value.isEmpty()) {
        return null;
      }
      int port = 0;
      for (int i = 0; i < value.length(); i++) {
        char c = value.charAt(i);
        if (c < '0' || c > '9') {
          return null;
        }
        port = port * 10 + c - '0';
        if (port > 65535) {
          return null;
        }
      }
      return port;
    }

    private static String bracketIpv6Endpoints(String addressGroup) {
      StringBuilder result = new StringBuilder();
      for (String endpoint : addressGroup.split(",", -1)) {
        if (result.length() > 0) {
          result.append(',');
        }
        if (endpoint.indexOf(':') >= 0) {
          result.append('[').append(endpoint).append(']');
        } else {
          result.append(endpoint);
        }
      }
      return result.toString();
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

    private int defaultPort() {
      if ("http".equalsIgnoreCase(scheme)) {
        return 8123;
      }
      if ("https".equalsIgnoreCase(scheme)) {
        return 8443;
      }
      return -1;
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
