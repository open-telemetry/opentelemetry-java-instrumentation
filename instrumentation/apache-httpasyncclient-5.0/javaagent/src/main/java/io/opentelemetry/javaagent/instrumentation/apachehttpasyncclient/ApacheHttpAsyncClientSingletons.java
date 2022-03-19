/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpasyncclient;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.PeerServiceAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanStatusExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesExtractor;
import org.apache.http.HttpResponse;

public final class ApacheHttpAsyncClientSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.apache-httpasyncclient-4.1";

  private static final Instrumenter<ApacheHttpClientRequest, HttpResponse> INSTRUMENTER;

  static {
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
            .addAttributesExtractor(HttpClientAttributesExtractor.create(httpAttributesGetter))
            .addAttributesExtractor(NetClientAttributesExtractor.create(netAttributesGetter))
            .addAttributesExtractor(PeerServiceAttributesExtractor.create(netAttributesGetter))
            .addRequestMetrics(HttpClientMetrics.get())
            .newClientInstrumenter(HttpHeaderSetter.INSTANCE);
  }

  public static Instrumenter<ApacheHttpClientRequest, HttpResponse> instrumenter() {
    return INSTRUMENTER;
  }

  private ApacheHttpAsyncClientSingletons() {}
}
