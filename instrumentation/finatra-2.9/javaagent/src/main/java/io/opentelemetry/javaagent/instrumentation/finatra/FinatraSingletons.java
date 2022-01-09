/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.finatra;

import com.twitter.finatra.http.contexts.RouteInfo;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.server.ServerSpanNaming;
import io.opentelemetry.instrumentation.api.tracer.ClassNames;

public final class FinatraSingletons {

  private static final Instrumenter<Class<?>, Void> INSTRUMENTER =
      Instrumenter.<Class<?>, Void>builder(
              GlobalOpenTelemetry.get(), "io.opentelemetry.finatra-2.9", ClassNames::simpleName)
          .newInstrumenter();

  public static Instrumenter<Class<?>, Void> instrumenter() {
    return INSTRUMENTER;
  }

  public static void updateServerSpanName(Context context, RouteInfo routeInfo) {
    ServerSpanNaming.updateServerSpanName(
        context, ServerSpanNaming.Source.CONTROLLER, (c, route) -> route.path(), routeInfo);
  }

  private FinatraSingletons() {}
}
