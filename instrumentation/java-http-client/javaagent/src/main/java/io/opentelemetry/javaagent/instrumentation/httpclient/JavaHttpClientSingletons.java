/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.httpclient;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.httpclient.internal.HttpHeadersSetter;
import io.opentelemetry.instrumentation.httpclient.internal.JavaHttpClientInstrumenterBuilderFactory;
import io.opentelemetry.javaagent.bootstrap.internal.JavaagentHttpClientInstrumenters;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class JavaHttpClientSingletons {

  private static final HttpHeadersSetter SETTER;
  private static final Instrumenter<HttpRequest, HttpResponse<?>> INSTRUMENTER;

  static {
    SETTER = new HttpHeadersSetter(GlobalOpenTelemetry.getPropagators());

    INSTRUMENTER =
        JavaagentHttpClientInstrumenters.create(
            JavaHttpClientInstrumenterBuilderFactory.create(GlobalOpenTelemetry.get()));
  }

  public static Instrumenter<HttpRequest, HttpResponse<?>> instrumenter() {
    return INSTRUMENTER;
  }

  public static HttpHeadersSetter setter() {
    return SETTER;
  }

  private JavaHttpClientSingletons() {}
}
