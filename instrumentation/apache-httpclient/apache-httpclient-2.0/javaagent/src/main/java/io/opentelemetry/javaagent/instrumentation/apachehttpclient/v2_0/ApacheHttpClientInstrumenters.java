/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v2_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanStatusExtractor;
import org.apache.commons.httpclient.HttpMethod;

public final class ApacheHttpClientInstrumenters {
  private static final String INSTRUMENTATION_NAME =
      "io.opentelemetry.javaagent.apache-httpclient-2.0";

  private static final Instrumenter<HttpMethod, Void> INSTRUMENTER;

  static {
    HttpAttributesExtractor<HttpMethod, Void> httpAttributesExtractor =
        new ApacheHttpClientHttpAttributesExtractor();
    SpanNameExtractor<? super HttpMethod> spanNameExtractor =
        HttpSpanNameExtractor.create(httpAttributesExtractor);
    SpanStatusExtractor<? super HttpMethod, ? super Void> spanStatusExtractor =
        HttpSpanStatusExtractor.create(httpAttributesExtractor);

    INSTRUMENTER =
        Instrumenter.<HttpMethod, Void>newBuilder(
                GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, spanNameExtractor)
            .setSpanStatusExtractor(spanStatusExtractor)
            .addAttributesExtractor(httpAttributesExtractor)
            .addAttributesExtractor(new ApacheHttpClientNetAttributesExtractor())
            .newClientInstrumenter(HttpHeaderSetter.INSTANCE);
  }

  public static Instrumenter<HttpMethod, Void> instrumenter() {
    return INSTRUMENTER;
  }

  private ApacheHttpClientInstrumenters() {}
}
