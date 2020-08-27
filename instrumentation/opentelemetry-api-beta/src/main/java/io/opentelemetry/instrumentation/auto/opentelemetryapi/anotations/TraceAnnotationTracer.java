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

package io.opentelemetry.instrumentation.auto.opentelemetryapi.anotations;

import application.io.opentelemetry.extensions.auto.annotations.WithSpan;
import application.io.opentelemetry.trace.Span;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import io.opentelemetry.trace.Span.Kind;
import java.lang.reflect.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TraceAnnotationTracer extends BaseTracer {
  public static final TraceAnnotationTracer TRACER = new TraceAnnotationTracer();

  private static final Logger log = LoggerFactory.getLogger(TraceAnnotationTracer.class);

  /**
   * This method is used to generate an acceptable span (operation) name based on a given method
   * reference. It first checks for existence of {@link WithSpan} annotation. If it is present, then
   * tries to derive name from its {@code value} attribute. Otherwise delegates to {@link
   * #spanNameForMethod(Method)}.
   */
  public String spanNameForMethodWithAnnotation(WithSpan applicationAnnotation, Method method) {
    if (applicationAnnotation != null && !applicationAnnotation.value().isEmpty()) {
      return applicationAnnotation.value();
    }
    return spanNameForMethod(method);
  }

  public Kind extractSpanKind(WithSpan applicationAnnotation) {
    Span.Kind applicationKind =
        applicationAnnotation != null ? applicationAnnotation.kind() : Span.Kind.INTERNAL;
    return toAgentOrNull(applicationKind);
  }

  public static Kind toAgentOrNull(Span.Kind applicationSpanKind) {
    try {
      return Kind.valueOf(applicationSpanKind.name());
    } catch (IllegalArgumentException e) {
      log.debug("unexpected span kind: {}", applicationSpanKind.name());
      return Kind.INTERNAL;
    }
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.auto.trace-annotation";
  }
}
