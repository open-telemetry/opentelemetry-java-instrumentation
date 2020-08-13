/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.auto.instrumentation.play.v2_6;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.instrumentation.api.decorator.BaseTracer;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.UndeclaredThrowableException;
import play.api.mvc.Request;
import play.api.routing.HandlerDef;
import play.libs.typedmap.TypedKey;
import play.routing.Router;
import scala.Option;

public class PlayTracer extends BaseTracer {
  public static final PlayTracer DECORATE = new PlayTracer();

  public static final Tracer TRACER = OpenTelemetry.getTracer("io.opentelemetry.auto.play-2.6");

  private static final Method typedKeyGetUnderlying;

  static {
    Method typedKeyGetUnderlyingCheck = null;
    try {
      // This method was added in Play 2.6.8
      typedKeyGetUnderlyingCheck = TypedKey.class.getMethod("asScala");
    } catch (final NoSuchMethodException ignored) {
    }
    // Fallback
    if (typedKeyGetUnderlyingCheck == null) {
      try {
        typedKeyGetUnderlyingCheck = TypedKey.class.getMethod("underlying");
      } catch (final NoSuchMethodException ignored) {
      }
    }
    typedKeyGetUnderlying = typedKeyGetUnderlyingCheck;
  }

  public void updateSpanName(final Span span, final Request request) {
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
        } catch (final IllegalAccessException | InvocationTargetException ignored) {
        }
      }
      if (defOption != null && !defOption.isEmpty()) {
        String path = defOption.get().path();
        span.updateName(request.method() + " " + path);
      }
    }
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.auto.play-2.6";
  }

  @Override
  protected Throwable unwrapThrowable(Throwable throwable) {
    // This can be moved to instanceof check when using Java 8.
    if (throwable.getClass().getName().equals("java.util.concurrent.CompletionException")
        && throwable.getCause() != null) {
      throwable = throwable.getCause();
    }
    while ((throwable instanceof InvocationTargetException
            || throwable instanceof UndeclaredThrowableException)
        && throwable.getCause() != null) {
      throwable = throwable.getCause();
    }
    return throwable;
  }
}
