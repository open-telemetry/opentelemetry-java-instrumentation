/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.httpurlconnection;

import static io.opentelemetry.javaagent.instrumentation.httpurlconnection.HeadersInjectAdapter.SETTER;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.PeerServiceAttributesExtractor;
import java.net.HttpURLConnection;

public class HttpUrlConnectionSingletons {

  private static final Instrumenter<HttpURLConnection, Integer> INSTRUMENTER;

  static {
    HttpUrlHttpAttributesExtractor httpAttributesExtractor = new HttpUrlHttpAttributesExtractor();
    HttpUrlNetAttributesExtractor netAttributesExtractor = new HttpUrlNetAttributesExtractor();
    SpanNameExtractor<HttpURLConnection> spanNameExtractor =
        HttpSpanNameExtractor.create(httpAttributesExtractor);

    INSTRUMENTER =
        Instrumenter.<HttpURLConnection, Integer>newBuilder(
                GlobalOpenTelemetry.get(),
                "io.opentelemetry.http-url-connection",
                spanNameExtractor)
            .addAttributesExtractor(httpAttributesExtractor)
            .addAttributesExtractor(netAttributesExtractor)
            .addAttributesExtractor(PeerServiceAttributesExtractor.create(netAttributesExtractor))
            .newClientInstrumenter(SETTER);
  }

  public static Instrumenter<HttpURLConnection, Integer> instrumenter() {
    return INSTRUMENTER;
  }
}
