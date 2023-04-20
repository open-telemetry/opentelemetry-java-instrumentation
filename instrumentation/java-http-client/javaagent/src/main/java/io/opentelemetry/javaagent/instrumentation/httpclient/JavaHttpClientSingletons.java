/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.httpclient;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.net.PeerServiceAttributesExtractor;
import io.opentelemetry.instrumentation.httpclient.internal.HttpHeadersSetter;
import io.opentelemetry.instrumentation.httpclient.internal.JavaHttpClientInstrumenterFactory;
import io.opentelemetry.instrumentation.httpclient.internal.JavaHttpClientNetAttributesGetter;
import io.opentelemetry.javaagent.bootstrap.internal.CommonConfig;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;

public class JavaHttpClientSingletons {

  private static final HttpHeadersSetter SETTER;
  private static final Instrumenter<HttpRequest, HttpResponse<?>> INSTRUMENTER;

  static {
    SETTER = new HttpHeadersSetter(GlobalOpenTelemetry.getPropagators());

    JavaHttpClientNetAttributesGetter netAttributesGetter = new JavaHttpClientNetAttributesGetter();

    INSTRUMENTER =
        JavaHttpClientInstrumenterFactory.createInstrumenter(
            GlobalOpenTelemetry.get(),
            CommonConfig.get().getClientRequestHeaders(),
            CommonConfig.get().getClientResponseHeaders(),
            Arrays.asList(
                PeerServiceAttributesExtractor.create(
                    netAttributesGetter, CommonConfig.get().getPeerServiceMapping())));
  }

  public static Instrumenter<HttpRequest, HttpResponse<?>> instrumenter() {
    return INSTRUMENTER;
  }

  public static HttpHeadersSetter setter() {
    return SETTER;
  }

  private JavaHttpClientSingletons() {}
}
