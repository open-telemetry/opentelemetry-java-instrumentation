/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.restlet.v1_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.servlet.ServerSpanNameSupplier;
import io.opentelemetry.instrumentation.restlet.v1_0.RestletTracing;
import io.opentelemetry.javaagent.bootstrap.servlet.ServletContextPath;
import org.restlet.data.Request;
import org.restlet.data.Response;

public final class RestletSingletons {

  private static final Instrumenter<Request, Response> INSTRUMENTER =
      RestletTracing.create(GlobalOpenTelemetry.get()).getServerInstrumenter();

  private static final ServerSpanNameSupplier<String> SERVER_SPAN_NAME =
      (context, pattern) -> {
        if (pattern == null || pattern.equals("")) {
          return null;
        }

        return ServletContextPath.prepend(context, pattern);
      };

  public static Instrumenter<Request, Response> instrumenter() {
    return INSTRUMENTER;
  }

  public static ServerSpanNameSupplier<String> serverSpanName() {
    return SERVER_SPAN_NAME;
  }

  private RestletSingletons() {}
}
