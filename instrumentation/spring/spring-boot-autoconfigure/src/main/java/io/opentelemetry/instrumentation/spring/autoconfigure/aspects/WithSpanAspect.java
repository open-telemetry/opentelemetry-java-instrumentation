/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.aspects;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.extension.annotations.WithSpan;
import java.lang.reflect.Method;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

/**
 * Uses Spring-AOP to wrap methods marked by {@link WithSpan} in a {@link
 * io.opentelemetry.api.trace.Span}.
 *
 * <p>Ensure methods annotated with {@link WithSpan} are implemented on beans managed by the Spring
 * container.
 *
 * <p>Note: This Aspect uses spring-aop to proxy beans. Therefore the {@link WithSpan} annotation
 * can not be applied to constructors.
 */
@Aspect
public class WithSpanAspect {

  private final Tracer tracer;

  public WithSpanAspect(Tracer tracer) {
    this.tracer = tracer;
  }

  @Around("@annotation(io.opentelemetry.extension.annotations.WithSpan)")
  public Object traceMethod(ProceedingJoinPoint pjp) throws Throwable {
    MethodSignature signature = (MethodSignature) pjp.getSignature();
    Method method = signature.getMethod();
    WithSpan withSpan = method.getAnnotation(WithSpan.class);

    Context parent = Context.current();
    Span span =
        tracer
            .spanBuilder(getSpanName(withSpan, method))
            .setSpanKind(withSpan.kind())
            .setParent(parent)
            .startSpan();
    try (Scope ignored = parent.with(span).makeCurrent()) {
      return pjp.proceed();
    } catch (Throwable t) {
      span.setStatus(StatusCode.ERROR);
      span.recordException(t);
      throw t;
    } finally {
      span.end();
    }
  }

  private String getSpanName(WithSpan withSpan, Method method) {
    String spanName = withSpan.value();
    if (spanName.isEmpty()) {
      return method.getDeclaringClass().getSimpleName() + "." + method.getName();
    }
    return spanName;
  }
}
