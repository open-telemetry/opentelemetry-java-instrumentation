/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.v3_0.client;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.instrumentation.vertx.client.VertxClientInstrumenterFactory;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;

public final class VertxClientSingletons {

  private static final Instrumenter<HttpClientRequest, HttpClientResponse> INSTRUMENTER =
      VertxClientInstrumenterFactory.create(
          "io.opentelemetry.vertx-http-client-3.0", new Vertx3HttpAttributesGetter(), null);

  public static Instrumenter<HttpClientRequest, HttpClientResponse> instrumenter() {
    return INSTRUMENTER;
  }

  private VertxClientSingletons() {}
}
