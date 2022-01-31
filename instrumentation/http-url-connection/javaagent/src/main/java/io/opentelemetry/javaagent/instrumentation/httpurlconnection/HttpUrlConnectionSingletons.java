/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.httpurlconnection;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.PeerServiceAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesExtractor;
import java.net.HttpURLConnection;

public final class HttpUrlConnectionSingletons {

  private static final Instrumenter<HttpURLConnection, Integer> INSTRUMENTER;

  static {
    HttpUrlHttpAttributesGetter httpAttributesGetter = new HttpUrlHttpAttributesGetter();
    HttpUrlNetAttributesGetter netAttributesGetter = new HttpUrlNetAttributesGetter();

    INSTRUMENTER =
        Instrumenter.<HttpURLConnection, Integer>builder(
                GlobalOpenTelemetry.get(),
                "io.opentelemetry.http-url-connection",
                HttpSpanNameExtractor.create(httpAttributesGetter))
            .addAttributesExtractor(HttpClientAttributesExtractor.create(httpAttributesGetter))
            .addAttributesExtractor(NetClientAttributesExtractor.create(netAttributesGetter))
            .addAttributesExtractor(PeerServiceAttributesExtractor.create(netAttributesGetter))
            .addRequestMetrics(HttpClientMetrics.get())
            .newClientInstrumenter(RequestPropertySetter.INSTANCE);
  }

  public static Instrumenter<HttpURLConnection, Integer> instrumenter() {
    return INSTRUMENTER;
  }

  private HttpUrlConnectionSingletons() {}
}
