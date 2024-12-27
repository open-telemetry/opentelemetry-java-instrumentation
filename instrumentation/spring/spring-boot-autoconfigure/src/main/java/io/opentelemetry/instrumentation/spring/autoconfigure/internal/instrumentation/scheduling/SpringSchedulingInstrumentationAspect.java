/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.scheduling;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeSpanNameExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.util.ClassAndMethod;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.framework.AopProxyUtils;

/**
 * Spring Scheduling instrumentation aop.
 *
 * <p>This aspect would intercept all methods annotated with {@link
 * org.springframework.scheduling.annotation.Scheduled} and {@link
 * org.springframework.scheduling.annotation.Schedules}.
 *
 * <p>Normally this would cover most of the Spring Scheduling use cases, but if you register jobs
 * programmatically such as {@link
 * org.springframework.scheduling.config.ScheduledTaskRegistrar#addCronTask}, this aspect would not
 * cover them. You may use {@link io.opentelemetry.instrumentation.annotations.WithSpan} to trace
 * these jobs manually.
 */
@Aspect
final class SpringSchedulingInstrumentationAspect {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.spring-boot-autoconfigure";
  private final Instrumenter<ClassAndMethod, Object> instrumenter;

  public SpringSchedulingInstrumentationAspect(
      OpenTelemetry openTelemetry, ConfigProperties configProperties) {
    CodeAttributesGetter<ClassAndMethod> codedAttributesGetter =
        ClassAndMethod.codeAttributesGetter();
    InstrumenterBuilder<ClassAndMethod, Object> builder =
        Instrumenter.builder(
                openTelemetry,
                INSTRUMENTATION_NAME,
                CodeSpanNameExtractor.create(codedAttributesGetter))
            .addAttributesExtractor(CodeAttributesExtractor.create(codedAttributesGetter));
    if (configProperties.getBoolean(
        "otel.instrumentation.spring-scheduling.experimental-span-attributes", false)) {
      builder.addAttributesExtractor(
          AttributesExtractor.constant(AttributeKey.stringKey("job.system"), "spring_scheduling"));
    }
    instrumenter = builder.buildInstrumenter();
  }

  @Pointcut(
      "@annotation(org.springframework.scheduling.annotation.Scheduled)"
          + "|| @annotation(org.springframework.scheduling.annotation.Schedules)")
  public void pointcut() {
    // ignored
  }

  @Around("pointcut()")
  public Object execution(ProceedingJoinPoint joinPoint) throws Throwable {
    Context parent = Context.current();
    ClassAndMethod request =
        ClassAndMethod.create(
            AopProxyUtils.ultimateTargetClass(joinPoint.getTarget()),
            ((MethodSignature) joinPoint.getSignature()).getMethod().getName());
    if (!instrumenter.shouldStart(parent, request)) {
      return joinPoint.proceed();
    }
    Context context = instrumenter.start(parent, request);

    Object object;
    try (Scope ignored = context.makeCurrent()) {
      object = joinPoint.proceed();
    } catch (Throwable t) {
      instrumenter.end(context, request, null, t);
      throw t;
    }
    instrumenter.end(context, request, object, null);
    return object;
  }
}
