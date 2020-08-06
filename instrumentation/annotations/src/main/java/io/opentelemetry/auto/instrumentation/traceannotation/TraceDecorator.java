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

package io.opentelemetry.auto.instrumentation.traceannotation;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.extensions.auto.annotations.WithSpan;
import io.opentelemetry.instrumentation.api.decorator.BaseDecorator;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Span.Kind;
import io.opentelemetry.trace.Tracer;
import java.lang.reflect.Method;

public class TraceDecorator extends BaseDecorator {
  public static final TraceDecorator DECORATE = new TraceDecorator();

  public static final Tracer TRACER =
      OpenTelemetry.getTracerProvider().get("io.opentelemetry.auto.trace-annotation");

  /**
   * This method is used to generate an acceptable span (operation) name based on a given method
   * reference. It first checks for existence of {@link WithSpan} annotation. If it is present, then
   * tries to derive name from its {@code value} attribute. Otherwise delegates to {@link
   * #spanNameForMethod(Method)}.
   */
  public String spanNameForMethodWithAnnotation(final Method method) {
    WithSpan annotation = method.getAnnotation(WithSpan.class);
    if (annotation != null && !annotation.value().isEmpty()) {
      return annotation.value();
    }

    return spanNameForMethod(method);
  }

  public Span.Kind extractSpanKind(final Method method) {
    WithSpan annotation = method.getAnnotation(WithSpan.class);
    return annotation != null ? annotation.kind() : Kind.INTERNAL;
  }
}
