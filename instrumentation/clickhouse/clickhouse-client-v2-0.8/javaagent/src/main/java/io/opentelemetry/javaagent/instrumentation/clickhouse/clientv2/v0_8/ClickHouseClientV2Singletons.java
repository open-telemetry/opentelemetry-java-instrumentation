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

  public static class ServerInfo {

    private static final ServerInfo EMPTY = new ServerInfo(null, null, null);

    @Nullable private final String address;
    @Nullable private final Integer port;
    @Nullable private final String addressGroup;

    private ServerInfo(
        @Nullable String address, @Nullable Integer port, @Nullable String addressGroup) {
      this.address = address;
      this.port = port;
      this.addressGroup = addressGroup;
    }

    public static ServerInfo empty() {
      return EMPTY;
    }

    static ServerInfo of(Set<String> endpoints) {
      if (endpoints.isEmpty()) {
        return EMPTY;
      }
      if (endpoints.size() == 1) {
        String endpoint = sanitizeEndpoint(endpoints.iterator().next());
        return new ServerInfo(endpointAddress(endpoint), endpointPort(endpoint), null);
      }

      // Endpoint iteration order is unspecified, so canonicalize the configured target.
      List<String> sanitized = new ArrayList<>(endpoints.size());
      for (String endpoint : endpoints) {
        sanitized.add(sanitizeEndpoint(endpoint));
      }
      sanitized.sort(String::compareTo);

      StringBuilder addressGroup = new StringBuilder();
      for (String endpoint : sanitized) {
        if (addressGroup.length() > 0) {
          addressGroup.append(',');
        }
        addressGroup.append(endpoint);
      }
      return new ServerInfo(null, null, addressGroup.toString());
    }

    public static ServerInfo ofCurrentEndpoint(Set<String> endpoints) {
      if (endpoints.isEmpty()) {
        return EMPTY;
      }
      String endpoint = endpoints.iterator().next();
      return new ServerInfo(UrlParser.getHost(endpoint), UrlParser.getPort(endpoint), null);
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

    private static String sanitizeEndpoint(String endpoint) {
      int authorityStart = endpoint.indexOf("://");
      authorityStart = authorityStart < 0 ? 0 : authorityStart + 3;
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
      return endpoint.substring(authorityStart, authorityEnd);
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
  }

  private static class CurrentServerInfo {
    private final ServerInfo serverInfo;

    private CurrentServerInfo(ServerInfo serverInfo) {
      this.serverInfo = serverInfo;
    }
  }

  private ClickHouseClientV2Singletons() {}
}
