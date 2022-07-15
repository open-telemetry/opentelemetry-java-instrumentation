/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.internal;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;

import java.util.List;
import java.util.Map;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class CommonConfig {

  private static final CommonConfig instance = new CommonConfig(InstrumentationConfig.get());

  public static CommonConfig get() {
    return instance;
  }

  private final Map<String, String> peerServiceMapping;
  private final List<String> clientRequestHeaders;
  private final List<String> clientResponseHeaders;
  private final List<String> serverRequestHeaders;
  private final List<String> serverResponseHeaders;

  CommonConfig(InstrumentationConfig config) {
    peerServiceMapping =
        config.getMap("otel.instrumentation.common.peer-service-mapping", emptyMap());
    clientRequestHeaders =
        config.getList("otel.instrumentation.http.capture-headers.client.request", emptyList());
    clientResponseHeaders =
        config.getList("otel.instrumentation.http.capture-headers.client.response", emptyList());
    serverRequestHeaders =
        config.getList("otel.instrumentation.http.capture-headers.server.request", emptyList());
    serverResponseHeaders =
        config.getList("otel.instrumentation.http.capture-headers.server.response", emptyList());
  }

  public Map<String, String> getPeerServiceMapping() {
    return peerServiceMapping;
  }

  public List<String> getClientRequestHeaders() {
    return clientRequestHeaders;
  }

  public List<String> getClientResponseHeaders() {
    return clientResponseHeaders;
  }

  public List<String> getServerRequestHeaders() {
    return serverRequestHeaders;
  }

  public List<String> getServerResponseHeaders() {
    return serverResponseHeaders;
  }
}
