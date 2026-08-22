/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.v3_0;

import static io.opentelemetry.javaagent.instrumentation.opensearch.v3_0.OpenSearchSingletons.restClient;
import static io.opentelemetry.javaagent.instrumentation.opensearch.v3_0.OpenSearchSingletons.serverAddress;

import java.net.URI;
import javax.annotation.Nullable;
import org.opensearch.client.transport.OpenSearchTransport;

public class OpenSearchServerAddress {

  private final String address;
  @Nullable private final Integer port;

  @Nullable
  static OpenSearchServerAddress get(OpenSearchTransport transport) {
    OpenSearchServerAddress stored = serverAddress(transport);
    if (stored != null) {
      return stored;
    }
    Object rc = restClient(transport);
    if (rc != null) {
      return OpenSearchNodeServerAddress.fromRestClientNodes(rc);
    }
    return null;
  }

  @Nullable
  public static OpenSearchServerAddress fromHost(String host) {
    try {
      URI uri = URI.create(host.contains("://") ? host : "https://" + host);
      String address = uri.getHost();
      if (address != null && address.startsWith("[") && address.endsWith("]")) {
        address = address.substring(1, address.length() - 1);
      }
      return create(address, uri.getPort());
    } catch (IllegalArgumentException ignored) {
      return null;
    }
  }

  public static void set(OpenSearchTransport transport, OpenSearchServerAddress server) {
    OpenSearchSingletons.setServerAddress(transport, server);
  }

  @Nullable
  static OpenSearchServerAddress create(@Nullable String address, int port) {
    if (address == null) {
      return null;
    }
    return new OpenSearchServerAddress(address, port >= 0 ? port : null);
  }

  private OpenSearchServerAddress(String address, @Nullable Integer port) {
    this.address = address;
    this.port = port;
  }

  String address() {
    return address;
  }

  @Nullable
  Integer port() {
    return port;
  }
}
