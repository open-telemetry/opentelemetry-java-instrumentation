/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v5_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanStatusExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.PeerServiceAttributesExtractor;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.bootstrap.internal.CommonConfig;

public final class ApacheHttpClientSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.apache-httpclient-5.0";

  private static final Instrumenter<ApacheHttpClientRequest, ApacheHttpClientResponse> INSTRUMENTER;
  private static final VirtualField<Context, ApacheContentLengthMetrics> metricsByContext;

  static {
    metricsByContext = VirtualField.find(Context.class, ApacheContentLengthMetrics.class);
    ApacheHttpClientHttpAttributesGetter httpAttributesGetter =
        new ApacheHttpClientHttpAttributesGetter();
    ApacheHttpClientNetAttributesGetter netAttributesGetter =
        new ApacheHttpClientNetAttributesGetter();

    INSTRUMENTER =
        Instrumenter.<ApacheHttpClientRequest, ApacheHttpClientResponse>builder(
                GlobalOpenTelemetry.get(),
                INSTRUMENTATION_NAME,
                HttpSpanNameExtractor.create(httpAttributesGetter))
            .setSpanStatusExtractor(HttpSpanStatusExtractor.create(httpAttributesGetter))
            .addAttributesExtractor(
                HttpClientAttributesExtractor.builder(httpAttributesGetter, netAttributesGetter)
                    .setCapturedRequestHeaders(CommonConfig.get().getClientRequestHeaders())
                    .setCapturedResponseHeaders(CommonConfig.get().getClientResponseHeaders())
                    .build())
            .addAttributesExtractor(
                PeerServiceAttributesExtractor.create(
                    netAttributesGetter, CommonConfig.get().getPeerServiceMapping()))
            .addAttributesExtractor(new ApacheContentLengthAttributesGetter())
            .addOperationMetrics(HttpClientMetrics.get())
            .buildClientInstrumenter(HttpHeaderSetter.INSTANCE);
  }

  public static Instrumenter<ApacheHttpClientRequest, ApacheHttpClientResponse> instrumenter() {
    return INSTRUMENTER;
  }

  public static ApacheContentLengthMetrics createOrGetContentLengthMetrics(Context parentContext) {
    ApacheContentLengthMetrics metrics = metricsByContext.get(parentContext);
    if (metrics == null) {
      metrics = new ApacheContentLengthMetrics();
      metricsByContext.set(parentContext, metrics);
    }
    return metrics;
  }

  public static ApacheContentLengthMetrics getContentLengthMetrics(Context parentContext) {
    return metricsByContext.get(parentContext);
  }

  private ApacheHttpClientSingletons() {}
}
