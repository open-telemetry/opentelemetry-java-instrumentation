/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.restlet.v1_1;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerRouteGetter;
import io.opentelemetry.instrumentation.restlet.v1_1.RestletTelemetry;
import io.opentelemetry.javaagent.bootstrap.internal.CommonConfig;
import io.opentelemetry.javaagent.bootstrap.servlet.ServletContextPath;
import org.restlet.data.Request;
import org.restlet.data.Response;

public final class RestletSingletons {

  private static final Instrumenter<Request, Response> INSTRUMENTER =
      RestletTelemetry.builder(GlobalOpenTelemetry.get())
          .setCapturedRequestHeaders(CommonConfig.get().getServerRequestHeaders())
          .setCapturedResponseHeaders(CommonConfig.get().getServerResponseHeaders())
          .setKnownMethods(CommonConfig.get().getKnownHttpRequestMethods())
          .build()
          .getServerInstrumenter();

  public static Instrumenter<Request, Response> instrumenter() {
    return INSTRUMENTER;
  }

  public static HttpServerRouteGetter<String> serverSpanName() {
    return ServletContextPath::prepend;
  }

  private RestletSingletons() {}
}
