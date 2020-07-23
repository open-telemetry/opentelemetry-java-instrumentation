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

package io.opentelemetry.auto.instrumentation.play.v2_4;

import io.opentelemetry.auto.bootstrap.instrumentation.decorator.BaseTracer;
import io.opentelemetry.trace.Span;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.UndeclaredThrowableException;
import play.api.mvc.Request;
import scala.Option;

public class PlayTracer extends BaseTracer {
  public static final PlayTracer TRACER = new PlayTracer();

  public void updateSpanName(final Span span, final Request<?> request) {
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
