/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachehttpclient.v4_3;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.builder.HttpClientConfigBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import org.apache.http.HttpResponse;

/** A builder for {@link ApacheHttpClientTelemetry}. */
public final class ApacheHttpClientTelemetryBuilder
    extends HttpClientConfigBuilder<
        ApacheHttpClientTelemetryBuilder, ApacheHttpClientRequest, HttpResponse> {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.apache-httpclient-4.3";

  ApacheHttpClientTelemetryBuilder(OpenTelemetry openTelemetry) {
    super(openTelemetry, ApacheHttpClientHttpAttributesGetter.INSTANCE);
  }

  /**
   * Returns a new {@link ApacheHttpClientTelemetry} configured with this {@link
   * ApacheHttpClientTelemetryBuilder}.
   */
  public ApacheHttpClientTelemetry build() {
    // We manually inject because we need to inject internal requests for redirects.
    return new ApacheHttpClientTelemetry(
        instrumenterBuilder(INSTRUMENTATION_NAME)
            .buildInstrumenter(SpanKindExtractor.alwaysClient()),
        openTelemetry.getPropagators());
  }
}
