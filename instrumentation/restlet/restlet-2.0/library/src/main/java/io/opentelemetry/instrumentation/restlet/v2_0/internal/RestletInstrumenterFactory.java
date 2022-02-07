/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.restlet.v2_0.internal;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.http.CapturedHttpHeaders;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanStatusExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetServerAttributesExtractor;
import java.util.Collections;
import java.util.List;
import org.restlet.Request;
import org.restlet.Response;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class RestletInstrumenterFactory {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.restlet-2.0";

  public static Instrumenter<Request, Response> newServerInstrumenter(OpenTelemetry openTelemetry) {
    return newServerInstrumenter(
        openTelemetry, CapturedHttpHeaders.server(Config.get()), Collections.emptyList());
  }

  public static Instrumenter<Request, Response> newServerInstrumenter(
      OpenTelemetry openTelemetry,
      CapturedHttpHeaders capturedHttpHeaders,
      List<AttributesExtractor<Request, Response>> additionalExtractors) {

    RestletHttpAttributesGetter httpAttributesGetter = new RestletHttpAttributesGetter();
    RestletNetAttributesGetter netAttributesGetter = new RestletNetAttributesGetter();

    return Instrumenter.<Request, Response>builder(
            openTelemetry, INSTRUMENTATION_NAME, HttpSpanNameExtractor.create(httpAttributesGetter))
        .setSpanStatusExtractor(HttpSpanStatusExtractor.create(httpAttributesGetter))
        .addAttributesExtractor(
            HttpServerAttributesExtractor.create(httpAttributesGetter, capturedHttpHeaders))
        .addAttributesExtractor(NetServerAttributesExtractor.create(netAttributesGetter))
        .addAttributesExtractors(additionalExtractors)
        .addRequestMetrics(HttpServerMetrics.get())
        .newServerInstrumenter(new RestletHeadersGetter());
  }
}
