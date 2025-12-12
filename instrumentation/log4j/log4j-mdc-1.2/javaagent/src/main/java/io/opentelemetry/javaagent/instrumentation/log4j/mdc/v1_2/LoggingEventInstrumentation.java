/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.log4j.mdc.v1_2;

import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.api.incubator.log.LoggingContextConstants;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.bootstrap.internal.ConfiguredResourceAttributesHolder;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.log4j.spi.LoggingEvent;

public class LoggingEventInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.log4j.spi.LoggingEvent");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(isPublic())
            .and(named("getMDC"))
            .and(takesArguments(1))
            .and(takesArgument(0, String.class)),
        LoggingEventInstrumentation.class.getName() + "$GetMdcAdvice");
  }

  @SuppressWarnings("unused")
  public static class GetMdcAdvice {

    @AssignReturned.ToReturned
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static Object onExit(
        @Advice.This LoggingEvent event,
        @Advice.Argument(0) String key,
        @Advice.Return @Nullable Object returnValue) {

      if (returnValue != null) {
        return returnValue;
      }
      String traceIdKey =
          DeclarativeConfigUtil.getString(
                  GlobalOpenTelemetry.get(), "java", "common", "logging", "trace_id")
              .orElse(LoggingContextConstants.TRACE_ID);
      String spanIdKey =
          DeclarativeConfigUtil.getString(
                  GlobalOpenTelemetry.get(), "java", "common", "logging", "span_id")
              .orElse(LoggingContextConstants.SPAN_ID);
      String traceFlagsKey =
          DeclarativeConfigUtil.getString(
                  GlobalOpenTelemetry.get(), "java", "common", "logging", "trace_flags")
              .orElse(LoggingContextConstants.TRACE_FLAGS);
      boolean traceId = traceIdKey.equals(key);
      boolean spanId = spanIdKey.equals(key);
      boolean traceFlags = traceFlagsKey.equals(key);

      if (!traceId && !spanId && !traceFlags) {
        return ConfiguredResourceAttributesHolder.getAttributeValue(key);
      }
      Context context = VirtualFieldHelper.CONTEXT.get(event);
      if (context == null) {
        return null;
      }
      SpanContext spanContext = Java8BytecodeBridge.spanFromContext(context).getSpanContext();
      if (!spanContext.isValid()) {
        return null;
      }
      if (traceId) {
        return spanContext.getTraceId();
      }
      if (spanId) {
        return spanContext.getSpanId();
      }
      return spanContext.getTraceFlags().asHex();
    }
  }
}
