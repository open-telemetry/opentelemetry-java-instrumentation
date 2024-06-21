/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkahttp.server;

import akka.http.scaladsl.model.HttpRequest;
import akka.http.scaladsl.model.HttpResponse;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.bootstrap.internal.JavaagentHttpServerInstrumenterBuilder;
import io.opentelemetry.javaagent.instrumentation.akkahttp.AkkaHttpUtil;
import java.util.Optional;

public final class AkkaHttpServerSingletons {

  private static final Instrumenter<HttpRequest, HttpResponse> INSTRUMENTER;

  static {
    INSTRUMENTER = JavaagentHttpServerInstrumenterBuilder.create(
            AkkaHttpUtil.instrumentationName(),
            new AkkaHttpServerAttributesGetter(),
            Optional.of(AkkaHttpServerHeaders.INSTANCE));
  }

  public static Instrumenter<HttpRequest, HttpResponse> instrumenter() {
    return INSTRUMENTER;
  }

  public static HttpResponse errorResponse() {
    return (HttpResponse) HttpResponse.create().withStatus(500);
  }

  private AkkaHttpServerSingletons() {}
}
