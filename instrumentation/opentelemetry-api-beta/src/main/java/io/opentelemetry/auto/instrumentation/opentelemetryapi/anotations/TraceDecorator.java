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

package io.opentelemetry.auto.instrumentation.opentelemetryapi.anotations;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.instrumentation.api.decorator.BaseDecorator;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;
import java.lang.reflect.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import unshaded.io.opentelemetry.extensions.auto.annotations.WithSpan;
import unshaded.io.opentelemetry.trace.Span.Kind;

public class TraceDecorator extends BaseDecorator {
  public static final TraceDecorator DECORATE = new TraceDecorator();

  public static final Tracer TRACER =
      OpenTelemetry.getTracerProvider().get("io.opentelemetry.auto.trace-annotation");

  private static final Logger log = LoggerFactory.getLogger(TraceDecorator.class);

  /**
   * This method is used to generate an acceptable span (operation) name based on a given method
   * reference. It first checks for existence of {@link WithSpan} annotation. If it is present, then
   * tries to derive name from its {@code value} attribute. Otherwise delegates to {@link
   * #spanNameForMethod(Method)}.
   */
  public String spanNameForMethodWithAnnotation(WithSpan annotation, Method method) {
    if (annotation != null && !annotation.value().isEmpty()) {
      return annotation.value();
    }
    return spanNameForMethod(method);
  }

  public Span.Kind extractSpanKind(WithSpan annotation) {
    Kind unshadedKind = annotation != null ? annotation.kind() : Kind.INTERNAL;
    return toShadedOrNull(unshadedKind);
  }

  public static io.opentelemetry.trace.Span.Kind toShadedOrNull(
      final unshaded.io.opentelemetry.trace.Span.Kind unshadedSpanKind) {
    try {
      return io.opentelemetry.trace.Span.Kind.valueOf(unshadedSpanKind.name());
    } catch (final IllegalArgumentException e) {
      log.debug("unexpected span kind: {}", unshadedSpanKind.name());
      return io.opentelemetry.trace.Span.Kind.INTERNAL;
    }
  }
}
