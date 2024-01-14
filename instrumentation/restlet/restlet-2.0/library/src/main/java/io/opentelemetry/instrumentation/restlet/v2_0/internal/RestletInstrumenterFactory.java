/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.restlet.v2_0.internal;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.http.HttpExperimentalAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.http.HttpServerExperimentalMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.ContextCustomizer;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerMetrics;
import io.opentelemetry.instrumentation.api.semconv.http.HttpSpanStatusExtractor;
import java.util.List;
import org.restlet.Request;
import org.restlet.Response;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class RestletInstrumenterFactory {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.restlet-2.0";

  public static Instrumenter<Request, Response> newServerInstrumenter(
      OpenTelemetry openTelemetry,
      AttributesExtractor<Request, Response> httpServerAttributesExtractor,
      SpanNameExtractor<Request> httpServerSpanNameExtractor,
      ContextCustomizer<Request> httpServerRoute,
      List<AttributesExtractor<Request, Response>> additionalExtractors,
      boolean emitExperimentalHttpServerMetrics) {

    RestletHttpAttributesGetter httpAttributesGetter = RestletHttpAttributesGetter.INSTANCE;

    InstrumenterBuilder<Request, Response> builder =
        Instrumenter.<Request, Response>builder(
                openTelemetry, INSTRUMENTATION_NAME, httpServerSpanNameExtractor)
            .setSpanStatusExtractor(HttpSpanStatusExtractor.create(httpAttributesGetter))
            .addAttributesExtractor(httpServerAttributesExtractor)
            .addAttributesExtractors(additionalExtractors)
            .addContextCustomizer(httpServerRoute)
            .addOperationMetrics(HttpServerMetrics.get());
    if (emitExperimentalHttpServerMetrics) {
      builder
          .addAttributesExtractor(HttpExperimentalAttributesExtractor.create(httpAttributesGetter))
          .addOperationMetrics(HttpServerExperimentalMetrics.get());
    }
    return builder.buildServerInstrumenter(new RestletHeadersGetter());
  }

  private RestletInstrumenterFactory() {}
}
