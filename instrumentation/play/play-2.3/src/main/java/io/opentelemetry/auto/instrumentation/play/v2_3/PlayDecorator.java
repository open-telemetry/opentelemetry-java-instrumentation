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

package io.opentelemetry.auto.instrumentation.play.v2_3;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.bootstrap.instrumentation.decorator.BaseDecorator;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.UndeclaredThrowableException;
import play.api.mvc.Request;
import scala.Option;

public class PlayDecorator extends BaseDecorator {
  public static final PlayDecorator DECORATE = new PlayDecorator();

  public static final Tracer TRACER =
      OpenTelemetry.getTracerProvider().get("io.opentelemetry.auto.play-2.4");

  public Span updateSpanName(final Span span, final Request request) {
    if (request != null) {
      // more about routes here:
      // https://github.com/playframework/playframework/blob/master/documentation/manual/releases/release26/migration26/Migration26.md#router-tags-are-now-attributes
      final Option pathOption = request.tags().get("ROUTE_PATTERN");
      if (!pathOption.isEmpty()) {
        final String path = (String) pathOption.get();
        span.updateName(request.method() + " " + path);
      }
    }
    return span;
  }

  @Override
  public Span onError(final Span span, Throwable throwable) {
    if (throwable != null
        // This can be moved to instanceof check when using Java 8.
        && throwable.getClass().getName().equals("java.util.concurrent.CompletionException")
        && throwable.getCause() != null) {
      throwable = throwable.getCause();
    }
    while ((throwable instanceof InvocationTargetException
            || throwable instanceof UndeclaredThrowableException)
        && throwable.getCause() != null) {
      throwable = throwable.getCause();
    }
    return super.onError(span, throwable);
  }
}
