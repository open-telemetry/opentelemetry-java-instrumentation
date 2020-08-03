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

package io.opentelemetry.instrumentation.spring.autoconfigure.aspects;

import io.opentelemetry.context.Scope;
import io.opentelemetry.extensions.auto.annotations.WithSpan;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Status;
import io.opentelemetry.trace.Tracer;
import java.lang.reflect.Method;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

@Aspect
public class WithSpanAspect {

  private final Tracer tracer;

  public WithSpanAspect(Tracer tracer) {
    this.tracer = tracer;
  }

  @Around("@annotation(io.opentelemetry.extensions.auto.annotations.WithSpan)")
  public Object traceMethod(final ProceedingJoinPoint pjp) throws Throwable {

    Span span = tracer.spanBuilder(getSpanName(pjp)).startSpan();

    try (Scope scope = tracer.withSpan(span)) {
      return pjp.proceed();
    } catch (Throwable t) {
      errorHandler(span, t);
      throw t;
    } finally {
      span.end();
    }
  }

  private String getSpanName(final ProceedingJoinPoint pjp) {
    MethodSignature signature = (MethodSignature) pjp.getSignature();
    Method method = signature.getMethod();
    WithSpan withSpan = method.getAnnotation(WithSpan.class);

    String spanName = withSpan.value();
    if (spanName.isEmpty()) {
      spanName = method.getName();
    }
    return spanName;
  }

  private static void errorHandler(Span span, Throwable t) {
    String message = t.getMessage();
    span.addEvent(message);
    span.setAttribute("error", true);
    span.setStatus(Status.UNKNOWN);
  }
}
