/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachehttpclient.v5_2;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.builder.AbstractHttpClientTelemetryBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import org.apache.hc.core5.http.HttpResponse;

/** A builder for {@link ApacheHttpClient5Telemetry}. */
public final class ApacheHttpClient5TelemetryBuilder extends
    AbstractHttpClientTelemetryBuilder<ApacheHttpClient5TelemetryBuilder, ApacheHttpClient5Request, HttpResponse> {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.apache-httpclient-5.2";

  ApacheHttpClient5TelemetryBuilder(OpenTelemetry openTelemetry) {
    super(INSTRUMENTATION_NAME, openTelemetry, ApacheHttpClient5HttpAttributesGetter.INSTANCE);
  }

  /**
   * Returns a new {@link ApacheHttpClient5Telemetry} configured with this {@link
   * ApacheHttpClient5TelemetryBuilder}.
   */
  public ApacheHttpClient5Telemetry build() {
    Instrumenter<ApacheHttpClient5Request, HttpResponse> instrumenter = instrumenterBuilder()
            // We manually inject because we need to inject internal requests for redirects.
            .buildInstrumenter(SpanKindExtractor.alwaysClient());

    return new ApacheHttpClient5Telemetry(instrumenter, openTelemetry.getPropagators());
  }
}
