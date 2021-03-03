/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.aspects;

import io.opentelemetry.api.OpenTelemetry;
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
  private final WithSpanAspectTracer tracer;

  public WithSpanAspect(OpenTelemetry openTelemetry) {
    tracer = new WithSpanAspectTracer(openTelemetry);
  }

  @Around("@annotation(io.opentelemetry.extension.annotations.WithSpan)")
  public Object traceMethod(ProceedingJoinPoint pjp) throws Throwable {
    MethodSignature signature = (MethodSignature) pjp.getSignature();
    Method method = signature.getMethod();
    WithSpan withSpan = method.getAnnotation(WithSpan.class);

    Context parentContext = Context.current();
    if (!tracer.shouldStartSpan(parentContext, withSpan.kind())) {
      return pjp.proceed();
    }

    Context context = tracer.startSpan(parentContext, withSpan, method);
    try (Scope ignored = context.makeCurrent()) {
      Object result = pjp.proceed();
      tracer.end(context);
      return result;
    } catch (Throwable t) {
      tracer.endExceptionally(context, t);
      throw t;
    }
  }
}
