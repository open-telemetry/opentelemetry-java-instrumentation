/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.play.v2_4;

import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import io.opentelemetry.trace.Span;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.UndeclaredThrowableException;
import play.api.mvc.Request;
import scala.Option;

public class PlayTracer extends BaseTracer {
  public static final PlayTracer TRACER = new PlayTracer();

  public void updateSpanName(Span span, Request<?> request) {
    if (request != null) {
      Option<String> pathOption = request.tags().get("ROUTE_PATTERN");
      if (!pathOption.isEmpty()) {
        String path = pathOption.get();
        span.updateName(request.method() + " " + path);
      }
    }
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.auto.play-2.4";
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
