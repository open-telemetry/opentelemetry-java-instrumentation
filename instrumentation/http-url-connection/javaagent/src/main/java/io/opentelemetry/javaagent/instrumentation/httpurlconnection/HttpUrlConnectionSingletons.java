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
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanStatusExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesExtractor;
import java.net.HttpURLConnection;

public final class HttpUrlConnectionSingletons {

  private static final Instrumenter<HttpURLConnection, Integer> INSTRUMENTER;

  private static final Instrumenter<HttpURLConnection, Integer> SUN_INSTRUMENTER;

  static {
    HttpUrlNetAttributesGetter netAttributesGetter = new HttpUrlNetAttributesGetter();

    HttpUrlHttpAttributesGetter httpAttributesGetter = new HttpUrlHttpAttributesGetter();
    INSTRUMENTER =
        Instrumenter.<HttpURLConnection, Integer>builder(
                GlobalOpenTelemetry.get(),
                "io.opentelemetry.http-url-connection",
                HttpSpanNameExtractor.create(httpAttributesGetter))
            .setSpanStatusExtractor(HttpSpanStatusExtractor.create(httpAttributesGetter))
            .addAttributesExtractor(HttpClientAttributesExtractor.create(httpAttributesGetter))
            .addAttributesExtractor(NetClientAttributesExtractor.create(netAttributesGetter))
            .addAttributesExtractor(PeerServiceAttributesExtractor.create(netAttributesGetter))
            .addOperationMetrics(HttpClientMetrics.get())
            .newClientInstrumenter(RequestPropertySetter.INSTANCE);

    SunHttpUrlHttpAttributesGetter sunHttpUrlHttpAttributesGetter =
        new SunHttpUrlHttpAttributesGetter();
    SUN_INSTRUMENTER =
        Instrumenter.<HttpURLConnection, Integer>builder(
                GlobalOpenTelemetry.get(),
                "io.opentelemetry.http-url-connection-sun",
                HttpSpanNameExtractor.create(sunHttpUrlHttpAttributesGetter))
            .setSpanStatusExtractor(HttpSpanStatusExtractor.create(sunHttpUrlHttpAttributesGetter))
            .addAttributesExtractor(
                HttpClientAttributesExtractor.create(sunHttpUrlHttpAttributesGetter))
            .addAttributesExtractor(NetClientAttributesExtractor.create(netAttributesGetter))
            .addAttributesExtractor(PeerServiceAttributesExtractor.create(netAttributesGetter))
            .addOperationMetrics(HttpClientMetrics.get())
            .newClientInstrumenter(RequestPropertySetter.INSTANCE);
  }

  private HttpUrlConnectionSingletons() {}

  public static Instrumenter<HttpURLConnection, Integer> instrumenter(
      Class<? extends HttpURLConnection> connectionClass, String methodName) {

    if (isGetOutputStreamMethodOfSunConnection(connectionClass, methodName)) {
      return SUN_INSTRUMENTER;
    }

    return INSTRUMENTER;
  }

  private static boolean isGetOutputStreamMethodOfSunConnection(
      Class<? extends HttpURLConnection> connectionClass, String methodName) {
    return connectionClass.getName().equals("sun.net.www.protocol.http.HttpURLConnection")
        && "getOutputStream".equals(methodName);
  }
}
