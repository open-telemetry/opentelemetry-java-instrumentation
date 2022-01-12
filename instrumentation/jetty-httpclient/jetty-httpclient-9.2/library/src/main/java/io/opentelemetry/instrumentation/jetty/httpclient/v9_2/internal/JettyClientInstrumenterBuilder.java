/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jetty.httpclient.v9_2.internal;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.CapturedHttpHeaders;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanStatusExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesExtractor;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;

public final class JettyClientInstrumenterBuilder {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.jetty-httpclient-9.2";

  private final OpenTelemetry openTelemetry;

  private final List<AttributesExtractor<? super Request, ? super Response>> additionalExtractors =
      new ArrayList<>();
  private CapturedHttpHeaders capturedHttpHeaders = CapturedHttpHeaders.client(Config.get());

  public JettyClientInstrumenterBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  public JettyClientInstrumenterBuilder addAttributeExtractor(
      AttributesExtractor<? super Request, ? super Response> attributesExtractor) {
    additionalExtractors.add(attributesExtractor);
    return this;
  }

  public JettyClientInstrumenterBuilder captureHttpHeaders(
      CapturedHttpHeaders capturedHttpHeaders) {
    this.capturedHttpHeaders = capturedHttpHeaders;
    return this;
  }

  public Instrumenter<Request, Response> build() {
    HttpClientAttributesExtractor<Request, Response> httpAttributesExtractor =
        new JettyClientHttpAttributesExtractor(capturedHttpHeaders);
    SpanNameExtractor<Request> spanNameExtractor =
        HttpSpanNameExtractor.create(httpAttributesExtractor);
    SpanStatusExtractor<Request, Response> spanStatusExtractor =
        HttpSpanStatusExtractor.create(httpAttributesExtractor);
    JettyHttpClientNetAttributesGetter netAttributesAdapter =
        new JettyHttpClientNetAttributesGetter();
    NetClientAttributesExtractor<Request, Response> attributesExtractor =
        NetClientAttributesExtractor.create(netAttributesAdapter);

    return Instrumenter.<Request, Response>builder(
            this.openTelemetry, INSTRUMENTATION_NAME, spanNameExtractor)
        .setSpanStatusExtractor(spanStatusExtractor)
        .addAttributesExtractor(httpAttributesExtractor)
        .addAttributesExtractor(attributesExtractor)
        .addAttributesExtractors(additionalExtractors)
        .addRequestMetrics(HttpClientMetrics.get())
        .newClientInstrumenter(HttpHeaderSetter.INSTANCE);
  }
}
