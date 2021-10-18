/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.play.v2_6;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import javax.annotation.Nullable;
import play.api.mvc.Request;
import play.api.routing.HandlerDef;
import play.libs.typedmap.TypedKey;
import play.routing.Router;
import scala.Option;

public class PlayTracer extends BaseTracer {
  private static final PlayTracer TRACER = new PlayTracer();

  public static PlayTracer tracer() {
    return TRACER;
  }

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

  public void updateSpanName(Span span, Request request) {
    if (request != null) {
      // more about routes here:
      // https://github.com/playframework/playframework/blob/master/documentation/manual/releases/release26/migration26/Migration26.md
      Option<HandlerDef> defOption = null;
      if (typedKeyGetUnderlying != null) { // Should always be non-null but just to make sure
        try {
          defOption =
              request
                  .attrs()
                  .get(
                      (play.api.libs.typedmap.TypedKey<HandlerDef>)
                          typedKeyGetUnderlying.invoke(Router.Attrs.HANDLER_DEF));
        } catch (IllegalAccessException | InvocationTargetException ignored) {
          // Ignore
        }
      }
      if (defOption != null && !defOption.isEmpty()) {
        String path = defOption.get().path();
        span.updateName(path);
      }
    }
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.play-2.6";
  }
}
