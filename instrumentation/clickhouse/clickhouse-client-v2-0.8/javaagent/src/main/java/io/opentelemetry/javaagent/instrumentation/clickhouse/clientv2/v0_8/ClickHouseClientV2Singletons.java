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
  private static final VirtualField<Client, ServerInfo> serverInfoField =
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
   * The endpoints the client was configured with. A client cannot be reconfigured, so the rendering
   * is computed once and kept on the client rather than on every query.
   */
  public static ServerInfo serverInfo(Client client) {
    ServerInfo serverInfo = serverInfoField.get(client);
    if (serverInfo == null) {
      serverInfo = ServerInfo.of(client.getEndpoints());
      serverInfoField.set(client, serverInfo);
    }
    return serverInfo;
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
      String first = sanitized.get(0);
      return new ServerInfo(
          UrlParser.getHost(first), UrlParser.getPort(first), addressGroup.toString());
    }

    /**
     * An endpoint reduced to {@code scheme://host[:port]}, keeping the scheme of each endpoint. The
     * client normalizes every endpoint it is given to a scheme, a host, a port and a path, so this
     * only has to drop what follows the authority.
     */
    private static String sanitizeEndpoint(String endpoint) {
      int authorityStart = endpoint.indexOf("://");
      if (authorityStart < 0) {
        return endpoint;
      }
      authorityStart += 3;
      for (int i = authorityStart; i < endpoint.length(); i++) {
        char c = endpoint.charAt(i);
        if (c == '/' || c == '?' || c == '#') {
          return endpoint.substring(0, i);
        }
      }
      return endpoint;
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
