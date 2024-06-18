/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.httpclient;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.builder.HttpClientConfigBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.httpclient.internal.HttpHeadersSetter;
import io.opentelemetry.instrumentation.httpclient.internal.JavaHttpClientAttributesGetter;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public final class JavaHttpClientTelemetryBuilder
    extends HttpClientConfigBuilder<JavaHttpClientTelemetryBuilder, HttpRequest, HttpResponse<?>> {

  public static final String INSTRUMENTATION_NAME = "io.opentelemetry.java-http-client";

  JavaHttpClientTelemetryBuilder(OpenTelemetry openTelemetry) {
    super(openTelemetry, JavaHttpClientAttributesGetter.INSTANCE);
  }

  public JavaHttpClientTelemetry build() {
    Instrumenter<HttpRequest, HttpResponse<?>> instrumenter =
        instrumenterBuilder(INSTRUMENTATION_NAME)
            .buildInstrumenter(SpanKindExtractor.alwaysClient());

    return new JavaHttpClientTelemetry(
        instrumenter, new HttpHeadersSetter(openTelemetry.getPropagators()));
  }
}
