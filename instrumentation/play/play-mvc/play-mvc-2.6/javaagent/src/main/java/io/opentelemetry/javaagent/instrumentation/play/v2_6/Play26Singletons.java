/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.play.v2_6;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.LocalRootSpan;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import javax.annotation.Nullable;
import play.api.mvc.Request;
import play.api.routing.HandlerDef;
import play.libs.typedmap.TypedKey;
import play.routing.Router;
import scala.Option;

public final class Play26Singletons {

  private static final String SPAN_NAME = "play.request";
  private static final Instrumenter<Void, Void> INSTRUMENTER =
      Instrumenter.<Void, Void>builder(
              GlobalOpenTelemetry.get(), "io.opentelemetry.play-mvc-2.6", s -> SPAN_NAME)
          .newInstrumenter();

  @Nullable private static final Method typedKeyGetUnderlying;

  static {
    Method typedKeyGetUnderlyingCheck = null;
    try {
      // This method was added in Play 2.6.8
      typedKeyGetUnderlyingCheck = TypedKey.class.getMethod("asScala");
    } catch (NoSuchMethodException ignored) {
      // Ignore
    }
    // Fallback
    if (typedKeyGetUnderlyingCheck == null) {
      try {
        typedKeyGetUnderlyingCheck = TypedKey.class.getMethod("underlying");
      } catch (NoSuchMethodException ignored) {
        // Ignore
      }
    }
    typedKeyGetUnderlying = typedKeyGetUnderlyingCheck;
  }

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
      // more about routes here:
      // https://github.com/playframework/playframework/blob/master/documentation/manual/releases/release26/migration26/Migration26.md
      Option<HandlerDef> defOption = null;
      if (typedKeyGetUnderlying != null) { // Should always be non-null but just to make sure
        try {
          @SuppressWarnings("unchecked")
          play.api.libs.typedmap.TypedKey<HandlerDef> handlerDef =
              (play.api.libs.typedmap.TypedKey<HandlerDef>)
                  typedKeyGetUnderlying.invoke(Router.Attrs.HANDLER_DEF);
          defOption = request.attrs().get(handlerDef);
        } catch (IllegalAccessException | InvocationTargetException ignored) {
          // Ignore
        }
      }
      if (defOption != null && !defOption.isEmpty()) {
        return defOption.get().path();
      }
    }
    return null;
  }

  private Play26Singletons() {}
}
