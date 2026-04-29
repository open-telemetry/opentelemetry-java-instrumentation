/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkahttp.v10_0.client;

import akka.http.scaladsl.model.HttpRequest;
import akka.http.scaladsl.model.HttpResponse;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.bootstrap.internal.JavaagentHttpClientInstrumenters;
import io.opentelemetry.javaagent.instrumentation.akkahttp.v10_0.AkkaHttpUtil;

class AkkaHttpClientSingletons {

  private static final HttpHeaderSetter headerSetter;
  private static final Instrumenter<HttpRequest, HttpResponse> instrumenter;

  static {
    headerSetter = new HttpHeaderSetter(GlobalOpenTelemetry.getPropagators());
    instrumenter =
        JavaagentHttpClientInstrumenters.create(
            AkkaHttpUtil.instrumentationName(), new AkkaHttpClientAttributesGetter());
  }

  static Instrumenter<HttpRequest, HttpResponse> instrumenter() {
    return instrumenter;
  }

  static HttpHeaderSetter setter() {
    return headerSetter;
  }

  private AkkaHttpClientSingletons() {}
}
