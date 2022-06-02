/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.play.v2_4;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.LocalRootSpan;
import play.api.mvc.Request;
import scala.Option;

public final class Play24Singletons {

  private static final String SPAN_NAME = "play.request";
  private static final Instrumenter<Void, Void> INSTRUMENTER =
      Instrumenter.<Void, Void>builder(
              GlobalOpenTelemetry.get(), "io.opentelemetry.play-mvc-2.4", s -> SPAN_NAME)
          .newInstrumenter();

  public static Instrumenter<Void, Void> instrumenter() {
    return INSTRUMENTER;
  }

  public static void updateSpanNames(Context context, Request<?> request) {
    String route = getRoute(request);
    if (route == null) {
      return;
    }

    Span.fromContext(context).updateName(route);
    // set the span name on the upstream akka/netty span
    Span serverSpan = LocalRootSpan.fromContextOrNull(context);
    if (serverSpan != null) {
      serverSpan.updateName(route);
    }
  }

  private static String getRoute(Request<?> request) {
    if (request != null) {
      Option<String> pathOption = request.tags().get("ROUTE_PATTERN");
      if (!pathOption.isEmpty()) {
        return pathOption.get();
      }
    }
    return null;
  }

  private Play24Singletons() {}
}
