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
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractorBuilder;
import io.opentelemetry.instrumentation.httpclient.internal.HttpHeadersSetter;
import io.opentelemetry.instrumentation.httpclient.internal.JavaHttpClientInstrumenterFactory;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;

public final class JavaHttpClientTelemetryBuilder {

  private final OpenTelemetry openTelemetry;

  private final List<AttributesExtractor<? super HttpRequest, ? super HttpResponse<?>>>
      additionalExtractors = new ArrayList<>();

  private List<String> capturedRequestHeaders = emptyList();
  private List<String> capturedResponseHeaders = emptyList();
  @Nullable private Set<String> knownMethods = null;

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

  /**
   * Configures the instrumentation to recognize an alternative set of HTTP request methods.
   *
   * <p>By default, this instrumentation defines "known" methods as the ones listed in <a
   * href="https://www.rfc-editor.org/rfc/rfc9110.html#name-methods">RFC9110</a> and the PATCH
   * method defined in <a href="https://www.rfc-editor.org/rfc/rfc5789.html">RFC5789</a>.
   *
   * <p>Note: calling this method <b>overrides</b> the default known method sets completely; it does
   * not supplement it.
   *
   * @param knownMethods A set of recognized HTTP request methods.
   * @see HttpClientAttributesExtractorBuilder#setKnownMethods(Set)
   */
  @CanIgnoreReturnValue
  public JavaHttpClientTelemetryBuilder setKnownMethods(Set<String> knownMethods) {
    this.knownMethods = knownMethods;
    return this;
  }

  public JavaHttpClientTelemetry build() {
    Instrumenter<HttpRequest, HttpResponse<?>> instrumenter =
        JavaHttpClientInstrumenterFactory.createInstrumenter(
            openTelemetry,
            capturedRequestHeaders,
            capturedResponseHeaders,
            knownMethods,
            additionalExtractors);

    return new JavaHttpClientTelemetry(
        instrumenter, new HttpHeadersSetter(openTelemetry.getPropagators()));
  }
}
