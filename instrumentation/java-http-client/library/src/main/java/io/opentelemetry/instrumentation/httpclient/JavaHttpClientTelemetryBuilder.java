/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.httpclient;

import static java.util.Collections.emptyList;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.httpclient.internal.HttpHeadersSetter;
import io.opentelemetry.instrumentation.httpclient.internal.JdkHttpInstrumenterFactory;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

public final class JavaHttpClientTelemetryBuilder {

  private final OpenTelemetry openTelemetry;

  private final List<AttributesExtractor<? super HttpRequest, ? super HttpResponse<?>>>
      additionalExtractors = new ArrayList<>();

  private List<String> capturedRequestHeaders = emptyList();
  private List<String> capturedResponseHeaders = emptyList();

  JavaHttpClientTelemetryBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  /**
   * Adds an additional {@link AttributesExtractor} to invoke to set attributes to instrumented
   * items. The {@link AttributesExtractor} will be executed after all default extractors.
   */
  @CanIgnoreReturnValue
  public JavaHttpClientTelemetryBuilder addAttributeExtractor(
      AttributesExtractor<? super HttpRequest, ? super HttpResponse<?>> attributesExtractor) {
    additionalExtractors.add(attributesExtractor);
    return this;
  }

  /**
   * Configures the HTTP client request headers that will be captured as span attributes.
   *
   * @param requestHeaders A list of HTTP header names.
   */
  @CanIgnoreReturnValue
  public JavaHttpClientTelemetryBuilder setCapturedRequestHeaders(List<String> requestHeaders) {
    capturedRequestHeaders = requestHeaders;
    return this;
  }

  /**
   * Configures the HTTP client response headers that will be captured as span attributes.
   *
   * @param responseHeaders A list of HTTP header names.
   */
  @CanIgnoreReturnValue
  public JavaHttpClientTelemetryBuilder setCapturedResponseHeaders(List<String> responseHeaders) {
    capturedResponseHeaders = responseHeaders;
    return this;
  }

  public JavaHttpClientTelemetry build() {
    Instrumenter<HttpRequest, HttpResponse<?>> instrumenter =
        JdkHttpInstrumenterFactory.createInstrumenter(
            openTelemetry, capturedRequestHeaders, capturedResponseHeaders, additionalExtractors);

    return new JavaHttpClientTelemetry(
        instrumenter, new HttpHeadersSetter(openTelemetry.getPropagators()));
  }
}
