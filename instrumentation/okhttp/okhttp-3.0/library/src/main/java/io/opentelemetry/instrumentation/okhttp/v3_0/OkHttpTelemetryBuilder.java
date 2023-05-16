/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.okhttp.v3_0;

import static java.util.Collections.emptyList;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.okhttp.v3_0.internal.OkHttpInstrumenterFactory;
import java.util.ArrayList;
import java.util.List;
import okhttp3.Request;
import okhttp3.Response;

/** A builder of {@link OkHttpTelemetry}. */
public final class OkHttpTelemetryBuilder {

  private final OpenTelemetry openTelemetry;
  private final List<AttributesExtractor<Request, Response>> additionalExtractors =
      new ArrayList<>();
  private List<String> capturedRequestHeaders = emptyList();
  private List<String> capturedResponseHeaders = emptyList();

  OkHttpTelemetryBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  /**
   * Adds an additional {@link AttributesExtractor} to invoke to set attributes to instrumented
   * items.
   */
  @CanIgnoreReturnValue
  public OkHttpTelemetryBuilder addAttributesExtractor(
      AttributesExtractor<Request, Response> attributesExtractor) {
    additionalExtractors.add(attributesExtractor);
    return this;
  }

  /**
   * Configures the HTTP request headers that will be captured as span attributes.
   *
   * @param requestHeaders A list of HTTP header names.
   */
  @CanIgnoreReturnValue
  public OkHttpTelemetryBuilder setCapturedRequestHeaders(List<String> requestHeaders) {
    capturedRequestHeaders = new ArrayList<>(requestHeaders);
    return this;
  }

  /**
   * Configures the HTTP response headers that will be captured as span attributes.
   *
   * @param responseHeaders A list of HTTP header names.
   */
  @CanIgnoreReturnValue
  public OkHttpTelemetryBuilder setCapturedResponseHeaders(List<String> responseHeaders) {
    capturedResponseHeaders = new ArrayList<>(responseHeaders);
    return this;
  }

  /**
   * Returns a new {@link OkHttpTelemetry} with the settings of this {@link OkHttpTelemetryBuilder}.
   */
  public OkHttpTelemetry build() {
    return new OkHttpTelemetry(
        OkHttpInstrumenterFactory.create(
            openTelemetry, capturedRequestHeaders, capturedResponseHeaders, additionalExtractors),
        openTelemetry.getPropagators());
  }
}
