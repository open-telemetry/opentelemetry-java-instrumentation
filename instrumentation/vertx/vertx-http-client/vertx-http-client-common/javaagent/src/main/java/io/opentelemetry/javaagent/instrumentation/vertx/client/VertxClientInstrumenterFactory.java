/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.client;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.bootstrap.internal.JavaagentHttpClientInstrumenters;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;

public final class VertxClientInstrumenterFactory {

  public static Instrumenter<HttpClientRequest, HttpClientResponse> create(
      String instrumentationName, AbstractVertxHttpAttributesGetter httpAttributesGetter) {

    return JavaagentHttpClientInstrumenters.create(
        instrumentationName, httpAttributesGetter, new HttpRequestHeaderSetter());
  }

  private VertxClientInstrumenterFactory() {}
}
