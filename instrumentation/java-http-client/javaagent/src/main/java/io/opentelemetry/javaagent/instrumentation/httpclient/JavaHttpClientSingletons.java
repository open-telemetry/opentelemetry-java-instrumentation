/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.httpclient;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.httpclient.JavaHttpClientTelemetryBuilder;
import io.opentelemetry.instrumentation.httpclient.internal.HttpHeadersSetter;
import io.opentelemetry.instrumentation.httpclient.internal.JavaHttpClientAttributesGetter;
import io.opentelemetry.javaagent.bootstrap.internal.JavaagentHttpClientInstrumenterBuilder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class JavaHttpClientSingletons {

  private static final HttpHeadersSetter SETTER;
  private static final Instrumenter<HttpRequest, HttpResponse<?>> INSTRUMENTER;

  static {
    SETTER = new HttpHeadersSetter(GlobalOpenTelemetry.getPropagators());

    INSTRUMENTER =
        JavaagentHttpClientInstrumenterBuilder.create(
                JavaHttpClientTelemetryBuilder.INSTRUMENTATION_NAME,
                JavaHttpClientAttributesGetter.INSTANCE)
            .buildInstrumenter(SpanKindExtractor.alwaysClient());
  }

  public static Instrumenter<HttpRequest, HttpResponse<?>> instrumenter() {
    return INSTRUMENTER;
  }

  public static HttpHeadersSetter setter() {
    return SETTER;
  }

  private JavaHttpClientSingletons() {}
}
