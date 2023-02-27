/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.restlet.v2_0.internal;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpRouteHolder;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanStatusExtractor;
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
      List<AttributesExtractor<Request, Response>> additionalExtractors) {

    RestletHttpAttributesGetter httpAttributesGetter = RestletHttpAttributesGetter.INSTANCE;

    return Instrumenter.<Request, Response>builder(
            openTelemetry, INSTRUMENTATION_NAME, HttpSpanNameExtractor.create(httpAttributesGetter))
        .setSpanStatusExtractor(HttpSpanStatusExtractor.create(httpAttributesGetter))
        .addAttributesExtractor(httpServerAttributesExtractor)
        .addAttributesExtractors(additionalExtractors)
        .addOperationMetrics(HttpServerMetrics.get())
        .addContextCustomizer(HttpRouteHolder.create(httpAttributesGetter))
        .buildServerInstrumenter(new RestletHeadersGetter());
  }

  private RestletInstrumenterFactory() {}
}
