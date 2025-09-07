/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.v3_0.client;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.instrumentation.vertx.client.Contexts;
import io.opentelemetry.javaagent.instrumentation.vertx.client.VertxClientInstrumenterFactory;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.impl.HttpClientImpl;

public final class VertxClientSingletons {

  private static final Instrumenter<HttpClientRequest, HttpClientResponse> INSTRUMENTER =
      VertxClientInstrumenterFactory.create(
          "io.opentelemetry.vertx-http-client-3.0", new Vertx3HttpAttributesGetter());

  public static final VirtualField<HttpClientRequest, Contexts> CONTEXTS =
      VirtualField.find(HttpClientRequest.class, Contexts.class);

  public static final VirtualField<HttpClientRequest, VertxRequestInfo> REQUEST_INFO =
      VirtualField.find(HttpClientRequest.class, VertxRequestInfo.class);

  public static final VirtualField<HttpClientImpl, HttpClientOptions> HTTP_CLIENT_OPTIONS =
      VirtualField.find(HttpClientImpl.class, HttpClientOptions.class);

  public static Instrumenter<HttpClientRequest, HttpClientResponse> instrumenter() {
    return INSTRUMENTER;
  }

  private VertxClientSingletons() {}
}
