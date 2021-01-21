/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.aspects;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.extension.annotations.WithSpan;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import io.opentelemetry.instrumentation.api.tracer.binding.DefaultTraceBinder;
import io.opentelemetry.instrumentation.api.tracer.binding.MethodSpan;
import io.opentelemetry.instrumentation.api.tracer.binding.SpanFinisher;
import io.opentelemetry.instrumentation.api.tracer.binding.TraceBinder;
import io.opentelemetry.instrumentation.api.tracer.binding.TraceBinding;
import io.opentelemetry.instrumentation.api.tracer.strategy.SpanStrategy;
import io.opentelemetry.instrumentation.api.tracer.strategy.SpanStrategyFactory;
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
  private final SpanStrategyFactory spanStrategyFactory;
  private final TraceBinder traceBinder;
  private final ConcurrentMap<Method, TraceBinding> traceBindingsMap;

  public WithSpanAspect(Tracer tracer) {
    this(tracer, new SpanStrategyFactory(), DefaultTraceBinder.INSTANCE);
  }

  public WithSpanAspect(Tracer tracer, SpanStrategyFactory spanStrategyFactory, TraceBinder traceBinder) {
    this.tracer = tracer;
    this.spanStrategyFactory = spanStrategyFactory;
    this.traceBinder = traceBinder;
    this.traceBindingsMap = new ConcurrentHashMap<>();
  }

  @Around("@annotation(io.opentelemetry.extension.annotations.WithSpan)")
  public Object traceMethod(ProceedingJoinPoint pjp) throws Throwable {
    MethodSignature signature = (MethodSignature) pjp.getSignature();
    Method method = signature.getMethod();
    WithSpan withSpan = method.getAnnotation(WithSpan.class);

    TraceBinding binding = traceBindingsMap.computeIfAbsent(method, key -> traceBinder.bind(method, withSpan));
    SpanBuilder spanBuilder = binding.apply(tracer, pjp::getArgs, pjp::proceed);
    SpanStrategy strategy = spanStrategyFactory.createSpanStrategy(signature.getReturnType());
    MethodSpan methodSpan = strategy.startMethodSpan(Context.current(), spanBuilder);

    try {
      Object result = pjp.proceed();
      return methodSpan.complete(result, null, endSpan(annotation));
    } catch (Exception exception) {
      methodSpan.complete(null, exception, endSpan(annotation));
      throw exception;
    }

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

  private SpanFinisher endSpan(WithSpan annotation) {
    return (span, result, error) -> {
      if (error == null || ignoreException(error, annotation.ignoredExceptions())) {
        span.setStatus(StatusCode.OK);
      } else {
        span.setStatus(StatusCode.ERROR, error.getMessage());
        span.recordException(error);
      }
      span.end();
      return result;
    };
  }

  private boolean ignoreException(Throwable error, Class<? extends Exception>[] ignoredExceptions) {
    if (error instanceof Exception) {
      for (Class<? extends Exception> ignoredException : ignoredExceptions) {
        if (ignoredException.isInstance(error)) {
          return true;
        }
      }
    }
    return false;
  }

  private String getSpanName(WithSpan withSpan, Method method) {
    String spanName = withSpan.value();
    if (spanName.isEmpty()) {
      return method.getDeclaringClass().getSimpleName() + "." + method.getName();
    }
    return spanName;
  }
}
