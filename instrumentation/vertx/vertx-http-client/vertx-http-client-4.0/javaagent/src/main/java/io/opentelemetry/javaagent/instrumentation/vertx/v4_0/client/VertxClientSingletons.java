/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.v4_0.client;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.instrumentation.vertx.client.Contexts;
import io.opentelemetry.javaagent.instrumentation.vertx.client.VertxClientInstrumenterFactory;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;

public final class VertxClientSingletons {

  private static final Instrumenter<HttpClientRequest, HttpClientResponse> INSTRUMENTER =
      VertxClientInstrumenterFactory.create(
          "io.opentelemetry.vertx-http-client-4.0", new Vertx4HttpAttributesGetter());

  public static final VirtualField<HttpClientRequest, Contexts> CONTEXTS =
      VirtualField.find(HttpClientRequest.class, Contexts.class);

  public static Instrumenter<HttpClientRequest, HttpClientResponse> instrumenter() {
    return INSTRUMENTER;
  }

  private VertxClientSingletons() {}
}
