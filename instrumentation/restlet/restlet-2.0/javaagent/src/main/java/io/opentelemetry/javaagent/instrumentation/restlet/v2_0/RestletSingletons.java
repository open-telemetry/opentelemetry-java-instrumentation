/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.restlet.v2_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRouteGetter;
import io.opentelemetry.instrumentation.restlet.v2_0.internal.RestletTelemetryBuilderFactory;
import io.opentelemetry.javaagent.bootstrap.internal.JavaagentHttpServerInstrumenters;
import io.opentelemetry.javaagent.bootstrap.servlet.ServletContextPath;
import org.restlet.Request;
import org.restlet.Response;

public final class RestletSingletons {

  private static final Instrumenter<Request, Response> INSTRUMENTER =
      JavaagentHttpServerInstrumenters.create(
          RestletTelemetryBuilderFactory.create(GlobalOpenTelemetry.get()));

  public static Instrumenter<Request, Response> instrumenter() {
    return INSTRUMENTER;
  }

  public static HttpServerRouteGetter<String> serverSpanName() {
    return ServletContextPath::prepend;
  }

  private RestletSingletons() {}
}
