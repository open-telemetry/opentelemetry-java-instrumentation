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
  private static final VirtualField<Client, DbServerTarget> CONFIGURED_SERVER_TARGET =
      VirtualField.find(Client.class, DbServerTarget.class);
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

  public static void captureConfiguredServerTarget(Client client) {
    CONFIGURED_SERVER_TARGET.set(client, parseConfiguredServerTarget(client.getEndpoints()));
  }

  // ClickHouseClientV2Test calls this reflectively to simulate a client whose endpoints were never
  // captured
  static void clearConfiguredServerTarget(Client client) {
    CONFIGURED_SERVER_TARGET.set(client, null);
  }

  @Nullable
  public static DbServerTarget configuredServerTarget(Client client) {
    return CONFIGURED_SERVER_TARGET.get(client);
  }

  public static CurrentServerInfo currentServerInfo(Client client) {
    CurrentServerInfo currentServerInfo = CURRENT_SERVER_INFO_FIELD.get(client);
    if (currentServerInfo == null) {
      currentServerInfo = CurrentServerInfo.of(client.getEndpoints());
      CURRENT_SERVER_INFO_FIELD.set(client, currentServerInfo);
    }
    return currentServerInfo;
  }

  @Nullable
  static DbServerTarget parseConfiguredServerTarget(Set<String> endpoints) {
    DbServerTargetBuilder builder = DbServerTarget.builder(-1).setSorted(true);
    for (String endpoint : endpoints) {
      EndpointTarget extracted = CurrentServerInfo.extractEndpoint(endpoint);
      if (extracted == null) {
        return null;
      }
      int defaultPort = extracted.defaultPort();
      builder.addEndpoint(
          extracted.address, extracted.port == null ? -1 : extracted.port, defaultPort);
    }
    return builder.build();
  }

  public static void capturePeer(ClickHouseDbRequest request, Object selectedNode)
      throws Exception {
    Class<?> selectedNodeClass = selectedNode.getClass();
    String host = (String) selectedNodeClass.getMethod("getHost").invoke(selectedNode);
    int port = (Integer) selectedNodeClass.getMethod("getPort").invoke(selectedNode);
    EndpointTarget extracted = CurrentServerInfo.extractEndpoint(host);
    request.setPeer(
        extracted == null ? null : CurrentServerInfo.peerServerTarget(extracted.address, port));
  }

  public static class CurrentServerInfo {
    private static final CurrentServerInfo EMPTY = new CurrentServerInfo(null, null, null);

    @Nullable private final String address;
    @Nullable private final Integer port;
    @Nullable private final DbServerTarget peer;

    private CurrentServerInfo(
        @Nullable String address, @Nullable Integer port, @Nullable DbServerTarget peer) {
      this.address = address;
      this.port = port;
      this.peer = peer;
    }

    private static CurrentServerInfo of(Set<String> endpoints) {
      if (endpoints.isEmpty()) {
        return EMPTY;
      }
      String endpoint = endpoints.iterator().next();
      EndpointTarget extracted = extractEndpoint(endpoint);
      DbServerTarget peer = extracted == null ? null : peerServerTarget(extracted);
      return new CurrentServerInfo(UrlParser.getHost(endpoint), UrlParser.getPort(endpoint), peer);
    }

    @Nullable
    private static DbServerTarget peerServerTarget(EndpointTarget endpoint) {
      return endpoint.port == null
          ? DbServerTarget.builder(endpoint.defaultPort()).addEndpoint(endpoint.address, -1).build()
          : peerServerTarget(endpoint.address, endpoint.port);
    }

    @Nullable
    private static DbServerTarget peerServerTarget(String address, int port) {
      return DbServerTarget.builder(-1).addEndpoint(address, port).build();
    }

    @Nullable
    private static EndpointTarget extractEndpoint(String endpoint) {
      if (hasWhitespace(endpoint)) {
        return null;
      }
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
      if (endpoint.lastIndexOf('@', authorityEnd - 1) >= authorityStart) {
        return null;
      }
      String authority = endpoint.substring(authorityStart, authorityEnd);
      if (authority.indexOf('=') >= 0
          || authority.indexOf(',') >= 0
          || hasUnsafePercentEscape(authority)) {
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

    private static boolean hasWhitespace(String value) {
      for (int i = 0; i < value.length(); i++) {
        if (Character.isWhitespace(value.charAt(i))) {
          return true;
        }
      }
      return false;
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
    public DbServerTarget getPeer() {
      return peer;
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

  private ClickHouseClientV2Singletons() {}
}
