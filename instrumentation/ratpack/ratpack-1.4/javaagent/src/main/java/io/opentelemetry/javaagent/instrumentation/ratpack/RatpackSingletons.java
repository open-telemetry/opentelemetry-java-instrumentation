/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ratpack;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.instrumentation.api.instrumenter.ErrorCauseExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRoute;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRouteSource;
import io.opentelemetry.javaagent.bootstrap.internal.ExperimentalConfig;
import ratpack.handling.Context;

public final class RatpackSingletons {

  private static final Instrumenter<String, Void> INSTRUMENTER =
      Instrumenter.<String, Void>builder(
              GlobalOpenTelemetry.get(), "io.opentelemetry.ratpack-1.4", s -> s)
          .setEnabled(ExperimentalConfig.get().controllerTelemetryEnabled())
          .buildInstrumenter();

  public static Instrumenter<String, Void> instrumenter() {
    return INSTRUMENTER;
  }

  public static void updateSpanNames(io.opentelemetry.context.Context otelContext, Context ctx) {
    String matchedRoute = updateServerSpanName(otelContext, ctx);
    // update ratpack span name
    if (ExperimentalConfig.get().controllerTelemetryEnabled()) {
      Span.fromContext(otelContext).updateName(matchedRoute);
    }
  }

  public static String updateServerSpanName(
      io.opentelemetry.context.Context otelContext, Context ctx) {
    String matchedRoute = ctx.getPathBinding().getDescription();
    if (matchedRoute == null || matchedRoute.isEmpty()) {
      matchedRoute = "/";
    } else if (!matchedRoute.startsWith("/")) {
      matchedRoute = "/" + matchedRoute;
    }

    // update the netty server span name; FILTER is probably the best match for ratpack Handlers
    HttpServerRoute.update(otelContext, HttpServerRouteSource.SERVER_FILTER, matchedRoute);
    return matchedRoute;
  }

  // copied from BaseTracer#onException()
  public static void onError(io.opentelemetry.context.Context context, Throwable error) {
    Span span = Span.fromContext(context);
    span.setStatus(StatusCode.ERROR);
    span.recordException(ErrorCauseExtractor.getDefault().extract(error));
  }

  private RatpackSingletons() {}
}
