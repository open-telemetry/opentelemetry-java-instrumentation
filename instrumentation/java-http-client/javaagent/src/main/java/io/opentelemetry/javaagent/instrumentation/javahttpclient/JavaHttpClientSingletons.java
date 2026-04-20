/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.javahttpclient;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.javahttpclient.internal.HttpHeadersSetter;
import io.opentelemetry.instrumentation.javahttpclient.internal.JavaHttpClientInstrumenterBuilderFactory;
import io.opentelemetry.javaagent.bootstrap.internal.JavaagentHttpClientInstrumenters;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class JavaHttpClientSingletons {

  private static final HttpHeadersSetter setter;
  private static final Instrumenter<HttpRequest, HttpResponse<?>> instrumenter;

  static {
    setter = new HttpHeadersSetter(GlobalOpenTelemetry.getPropagators());

    instrumenter =
        JavaagentHttpClientInstrumenters.create(
            JavaHttpClientInstrumenterBuilderFactory.create(GlobalOpenTelemetry.get()));
  }

  public static Instrumenter<HttpRequest, HttpResponse<?>> instrumenter() {
    return instrumenter;
  }

  public static HttpHeadersSetter setter() {
    return setter;
  }

  private JavaHttpClientSingletons() {}
}
