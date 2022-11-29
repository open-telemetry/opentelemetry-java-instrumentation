/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jetty.httpclient.v9_2.internal;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractorBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanStatusExtractor;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class JettyClientInstrumenterBuilder {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.jetty-httpclient-9.2";

  private final OpenTelemetry openTelemetry;

  private final List<AttributesExtractor<? super Request, ? super Response>> additionalExtractors =
      new ArrayList<>();
  private final HttpClientAttributesExtractorBuilder<Request, Response>
      httpAttributesExtractorBuilder =
          HttpClientAttributesExtractor.builder(
              JettyClientHttpAttributesGetter.INSTANCE, new JettyHttpClientNetAttributesGetter());

  public JettyClientInstrumenterBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  @CanIgnoreReturnValue
  public JettyClientInstrumenterBuilder addAttributeExtractor(
      AttributesExtractor<? super Request, ? super Response> attributesExtractor) {
    additionalExtractors.add(attributesExtractor);
    return this;
  }

  @CanIgnoreReturnValue
  public JettyClientInstrumenterBuilder setCapturedRequestHeaders(List<String> requestHeaders) {
    httpAttributesExtractorBuilder.setCapturedRequestHeaders(requestHeaders);
    return this;
  }

  @CanIgnoreReturnValue
  public JettyClientInstrumenterBuilder setCapturedResponseHeaders(List<String> responseHeaders) {
    httpAttributesExtractorBuilder.setCapturedResponseHeaders(responseHeaders);
    return this;
  }

  public Instrumenter<Request, Response> build() {
    JettyClientHttpAttributesGetter httpAttributesGetter = JettyClientHttpAttributesGetter.INSTANCE;

    return Instrumenter.<Request, Response>builder(
            this.openTelemetry,
            INSTRUMENTATION_NAME,
            HttpSpanNameExtractor.create(httpAttributesGetter))
        .setSpanStatusExtractor(HttpSpanStatusExtractor.create(httpAttributesGetter))
        .addAttributesExtractor(httpAttributesExtractorBuilder.build())
        .addAttributesExtractors(additionalExtractors)
        .addOperationMetrics(HttpClientMetrics.get())
        .buildClientInstrumenter(HttpHeaderSetter.INSTANCE);
  }
}
