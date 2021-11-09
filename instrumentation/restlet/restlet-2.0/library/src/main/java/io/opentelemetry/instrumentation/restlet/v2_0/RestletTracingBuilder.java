/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.restlet.v2_0;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.http.CapturedHttpHeaders;
import io.opentelemetry.instrumentation.restlet.v2_0.internal.RestletInstrumenterFactory;
import java.util.ArrayList;
import java.util.List;
import org.restlet.Request;
import org.restlet.Response;

/** A builder of {@link RestletTracing}. */
public final class RestletTracingBuilder {

  private final OpenTelemetry openTelemetry;
  private final List<AttributesExtractor<Request, Response>> additionalExtractors =
      new ArrayList<>();
  private CapturedHttpHeaders capturedHttpHeaders = CapturedHttpHeaders.server(Config.get());

  RestletTracingBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  /**
   * Adds an additional {@link AttributesExtractor} to invoke to set attributes to instrumented
   * items.
   */
  public RestletTracingBuilder addAttributesExtractor(
      AttributesExtractor<Request, Response> attributesExtractor) {
    additionalExtractors.add(attributesExtractor);
    return this;
  }

  /**
   * Configure the instrumentation to capture chosen HTTP request and response headers as span
   * attributes.
   *
   * @param capturedHttpHeaders An instance of {@link CapturedHttpHeaders} containing the configured
   *     HTTP request and response names.
   */
  public RestletTracingBuilder captureHttpHeaders(CapturedHttpHeaders capturedHttpHeaders) {
    this.capturedHttpHeaders = capturedHttpHeaders;
    return this;
  }

  /**
   * Returns a new {@link RestletTracing} with the settings of this {@link RestletTracingBuilder}.
   */
  public RestletTracing build() {

    Instrumenter<Request, Response> serverInstrumenter =
        RestletInstrumenterFactory.newServerInstrumenter(
            openTelemetry, capturedHttpHeaders, additionalExtractors);

    return new RestletTracing(serverInstrumenter);
  }
}
