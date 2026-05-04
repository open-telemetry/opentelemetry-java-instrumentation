/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkahttp.v10_0.server;

import akka.http.scaladsl.model.HttpRequest;
import akka.http.scaladsl.model.HttpResponse;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.bootstrap.internal.JavaagentHttpServerInstrumenters;
import io.opentelemetry.javaagent.instrumentation.akkahttp.v10_0.AkkaHttpUtil;

class AkkaHttpServerSingletons {

  private static final Instrumenter<HttpRequest, HttpResponse> instrumenter;

  static {
    instrumenter =
        JavaagentHttpServerInstrumenters.create(
            AkkaHttpUtil.instrumentationName(),
            new AkkaHttpServerAttributesGetter(),
            new AkkaHttpServerHeaders());
  }

  static Instrumenter<HttpRequest, HttpResponse> instrumenter() {
    return instrumenter;
  }

  static HttpResponse errorResponse() {
    return (HttpResponse) HttpResponse.create().withStatus(500);
  }

  private AkkaHttpServerSingletons() {}
}
