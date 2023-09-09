/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.finatra;

import com.twitter.finatra.http.contexts.RouteInfo;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.code.CodeAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.code.CodeSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerRoute;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerRouteSource;

public final class FinatraSingletons {

  private static final Instrumenter<Class<?>, Void> INSTRUMENTER;

  static {
    FinatraCodeAttributesGetter codeAttributesGetter = new FinatraCodeAttributesGetter();
    INSTRUMENTER =
        Instrumenter.<Class<?>, Void>builder(
                GlobalOpenTelemetry.get(),
                "io.opentelemetry.finatra-2.9",
                CodeSpanNameExtractor.create(codeAttributesGetter))
            .addAttributesExtractor(CodeAttributesExtractor.create(codeAttributesGetter))
            .buildInstrumenter();
  }

  public static Instrumenter<Class<?>, Void> instrumenter() {
    return INSTRUMENTER;
  }

  public static void updateServerSpanName(Context context, RouteInfo routeInfo) {
    HttpServerRoute.update(context, HttpServerRouteSource.CONTROLLER, routeInfo.path());
  }

  private FinatraSingletons() {}
}
