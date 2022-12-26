/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpasyncclient;

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
import org.apache.http.HttpResponse;

public final class ApacheHttpAsyncClientSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.apache-httpasyncclient-4.1";

  private static final Instrumenter<ApacheHttpClientRequest, HttpResponse> INSTRUMENTER;
  private static final VirtualField<Context, BytesTransferMetrics> metricsByContext;

  static {
    metricsByContext = VirtualField.find(Context.class, BytesTransferMetrics.class);
    ApacheHttpAsyncClientHttpAttributesGetter httpAttributesGetter =
        new ApacheHttpAsyncClientHttpAttributesGetter();
    ApacheHttpAsyncClientNetAttributesGetter netAttributesGetter =
        new ApacheHttpAsyncClientNetAttributesGetter();

    INSTRUMENTER =
        Instrumenter.<ApacheHttpClientRequest, HttpResponse>builder(
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
            .addAttributesExtractor(new ApacheHttpClientContentLengthAttributesGetter())
            .addOperationMetrics(HttpClientMetrics.get())
            .buildClientInstrumenter(HttpHeaderSetter.INSTANCE);
  }

  public static BytesTransferMetrics createOrGetBytesTransferMetrics(Context parentContext) {
    BytesTransferMetrics metrics = metricsByContext.get(parentContext);
    if (metrics == null) {
      metrics = new BytesTransferMetrics();
      metricsByContext.set(parentContext, metrics);
    }
    return metrics;
  }

  public static BytesTransferMetrics getBytesTransferMetrics(Context parentContext) {
    return metricsByContext.get(parentContext);
  }

  public static Instrumenter<ApacheHttpClientRequest, HttpResponse> instrumenter() {
    return INSTRUMENTER;
  }

  private ApacheHttpAsyncClientSingletons() {}
}
