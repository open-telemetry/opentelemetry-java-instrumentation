/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.aspects;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.extension.annotations.WithSpan;
import io.opentelemetry.instrumentation.api.annotation.support.MethodSpanAttributesExtractor;
import io.opentelemetry.instrumentation.api.annotation.support.async.AsyncOperationEndStrategies;
import io.opentelemetry.instrumentation.api.annotation.support.async.AsyncOperationEndStrategy;
import io.opentelemetry.instrumentation.api.annotation.support.async.AsyncOperationEndSupport;
import io.opentelemetry.instrumentation.api.caching.Cache;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.lang.reflect.Method;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.ParameterNameDiscoverer;

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
  private static final String INSTRUMENTATION_NAME = "spring-boot-autoconfigure";

  private final Instrumenter<JoinPoint, Object> instrumenter;

  public WithSpanAspect(
      OpenTelemetry openTelemetry, ParameterNameDiscoverer parameterNameDiscoverer) {

    instrumenter =
        Instrumenter.newBuilder(
                openTelemetry, INSTRUMENTATION_NAME, WithSpanAspectSpanNameExtractor.INSTANCE)
            .addAttributesExtractor(
                MethodSpanAttributesExtractor.builder(WithSpanAspect::method)
                    .setMethodCache(Cache.newBuilder().setWeakKeys().build())
                    .setParameterAttributeNamesExtractor(
                        new WithSpanAspectParameterAttributeNamesExtractor(parameterNameDiscoverer))
                    .build(JoinPoint::getArgs))
            .newInstrumenter(WithSpanAspectSpanKindExtractor.INSTANCE);
  }

  private static Method method(JoinPoint joinPoint) {
    MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
    return methodSignature.getMethod();
  }

  @Around("@annotation(io.opentelemetry.extension.annotations.WithSpan)")
  public Object traceMethod(ProceedingJoinPoint pjp) throws Throwable {

    Context parentContext = Context.current();
    if (!instrumenter.shouldStart(parentContext, pjp)) {
      return pjp.proceed();
    }

    Context context = instrumenter.start(parentContext, pjp);
    try (Scope ignored = context.makeCurrent()) {
      return end(pjp, context, pjp.proceed());
    } catch (Throwable t) {
      instrumenter.end(context, pjp, null, t);
      throw t;
    }
  }

  private Object end(JoinPoint joinPoint, Context context, Object response) {

    Class<?> returnType = method(joinPoint).getReturnType();

    if (returnType.isInstance(response)) {
      AsyncOperationEndStrategy asyncOperationEndStrategy =
          AsyncOperationEndStrategies.instance().resolveStrategy(returnType);

      if (asyncOperationEndStrategy != null) {
        AsyncOperationEndSupport<JoinPoint, Object> asyncOperationEndSupport =
            AsyncOperationEndSupport.create(instrumenter, Object.class, returnType);

        return asyncOperationEndSupport.asyncEnd(context, joinPoint, response, null);
      }
    }
    instrumenter.end(context, joinPoint, response, null);
    return response;
  }
}
