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
import java.util.Collections;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;

public class ClickHouseClientV2Singletons {

  private static final String INSTRUMENTER_NAME = "io.opentelemetry.clickhouse-client-v2-0.8";
  private static final Instrumenter<ClickHouseDbRequest, Void> instrumenter;
  private static final VirtualField<Client, ServerInfo> SERVER_INFO_FIELD =
      VirtualField.find(Client.class, ServerInfo.class);

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

  /**
   * Capture the endpoints that belong to a newly constructed client before its builder can be
   * changed or reused.
   */
  public static void captureServerInfo(Client client) {
    SERVER_INFO_FIELD.set(client, ServerInfo.of(client.getEndpoints()));
  }

  @Nullable
  public static ServerInfo serverInfo(Client client) {
    return SERVER_INFO_FIELD.get(client);
  }

  /**
   * The address a client resolves to: the single configured endpoint, or the whole endpoint list
   * when the client was given more than one.
   */
  public static final class ServerInfo {

    @Nullable private final String address;
    @Nullable private final Integer port;
    @Nullable private final String addressGroup;

    private ServerInfo(
        @Nullable String address, @Nullable Integer port, @Nullable String addressGroup) {
      this.address = address;
      this.port = port;
      this.addressGroup = addressGroup;
    }

    static ServerInfo of(Set<String> endpoints) {
      if (endpoints.isEmpty()) {
        return new ServerInfo(null, null, null);
      }
      if (endpoints.size() == 1) {
        String endpoint = endpoints.iterator().next();
        return new ServerInfo(UrlParser.getHost(endpoint), UrlParser.getPort(endpoint), null);
      }

      String first = sanitizeEndpoint(endpoints.iterator().next());

      // the endpoints of a client are an unordered set, so they are sorted to give one client
      // configuration one target, whatever order the set iterates in
      List<String> sanitized = new ArrayList<>(endpoints.size());
      for (String endpoint : endpoints) {
        sanitized.add(sanitizeEndpoint(endpoint));
      }
      Collections.sort(sanitized);

      StringBuilder addressGroup = new StringBuilder();
      for (String endpoint : sanitized) {
        if (addressGroup.length() > 0) {
          addressGroup.append(',');
        }
        addressGroup.append(endpoint);
      }
      return new ServerInfo(endpointAddress(first), endpointPort(first), addressGroup.toString());
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

    /** An endpoint reduced to {@code host[:port]}. */
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

  private ClickHouseClientV2Singletons() {}
}
