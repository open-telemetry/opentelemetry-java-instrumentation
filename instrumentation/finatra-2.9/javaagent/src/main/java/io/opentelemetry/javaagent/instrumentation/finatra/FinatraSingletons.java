/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.finatra;

import com.twitter.finatra.http.contexts.RouteInfo;
import com.twitter.finatra.http.internal.routing.Route;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.internal.ClassNames;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRoute;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRouteSource;
import io.opentelemetry.instrumentation.api.util.VirtualField;

public final class FinatraSingletons {

  private static final Instrumenter<FinatraRequest, Void> INSTRUMENTER;

  static {
    FinatraCodeAttributesGetter codeAttributesGetter = new FinatraCodeAttributesGetter();
    INSTRUMENTER =
        Instrumenter.<FinatraRequest, Void>builder(
                GlobalOpenTelemetry.get(),
                "io.opentelemetry.finatra-2.9",
                request ->
                    request.controllerClass() != null
                        ? ClassNames.simpleName(request.controllerClass())
                        : "<unknown>")
            .addAttributesExtractor(CodeAttributesExtractor.create(codeAttributesGetter))
            .buildInstrumenter();
  }

  public static Instrumenter<FinatraRequest, Void> instrumenter() {
    return INSTRUMENTER;
  }

  public static void updateServerSpanName(Context context, RouteInfo routeInfo) {
    HttpServerRoute.update(context, HttpServerRouteSource.CONTROLLER, routeInfo.path());
  }

  private static final VirtualField<Route, Class<?>> callbackClassField =
      VirtualField.find(Route.class, Class.class);

  public static void setCallbackClass(Route route, Class<?> clazz) {
    callbackClassField.set(route, clazz);
  }

  public static Class<?> getCallbackClass(Route route) {
    return callbackClassField.get(route);
  }

  private FinatraSingletons() {}
}
