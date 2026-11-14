/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.log4j.mdc.v1_2;

import static java.util.Collections.unmodifiableMap;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.bootstrap.internal.ConfiguredResourceAttributesHolder;
import io.opentelemetry.javaagent.bootstrap.internal.LogPatternInjection;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.log4j.spi.LoggingEvent;

class LoggingEventInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.log4j.spi.LoggingEvent");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isPublic().and(named("getMDC")).and(takesArgument(0, String.class)),
        getClass().getName() + "$GetMdcAdvice");
    transformer.applyAdviceToMethod(
        isPublic().and(named("getProperties")).and(takesArguments(0)),
        getClass().getName() + "$GetPropertiesAdvice");
  }

  @SuppressWarnings("unused")
  public static class GetPropertiesAdvice {
    @AssignReturned.ToReturned
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static Map<String, Object> onExit(
        @Advice.This LoggingEvent event, @Advice.Return Map<String, Object> properties) {
      if (!LogPatternInjection.isEnabled()) {
        return properties;
      }
      Map<String, Object> result = new HashMap<>(properties);
      for (Map.Entry<String, String> entry :
          ConfiguredResourceAttributesHolder.getResourceAttributes().entrySet()) {
        result.putIfAbsent(entry.getKey(), entry.getValue());
      }
      Context context = VirtualFieldHelper.CONTEXT.get(event);
      if (context != null) {
        SpanContext span = Span.fromContext(context).getSpanContext();
        if (span.isValid()) {
          result.putIfAbsent(AgentCommonConfig.get().getTraceIdKey(), span.getTraceId());
          result.putIfAbsent(AgentCommonConfig.get().getSpanIdKey(), span.getSpanId());
          result.putIfAbsent(
              AgentCommonConfig.get().getTraceFlagsKey(), span.getTraceFlags().asHex());
        }
      }
      return unmodifiableMap(result);
    }
  }

  @SuppressWarnings("unused")
  public static class GetMdcAdvice {

    @AssignReturned.ToReturned
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static Object onExit(
        @Advice.This LoggingEvent event,
        @Advice.Argument(0) String key,
        @Advice.Return @Nullable Object returnValue) {

      if (returnValue != null) {
        return returnValue;
      }
      boolean traceId = AgentCommonConfig.get().getTraceIdKey().equals(key);
      boolean spanId = AgentCommonConfig.get().getSpanIdKey().equals(key);
      boolean traceFlags = AgentCommonConfig.get().getTraceFlagsKey().equals(key);

      if (!traceId && !spanId && !traceFlags) {
        return ConfiguredResourceAttributesHolder.getAttributeValue(key);
      }
      Context context = VirtualFieldHelper.CONTEXT.get(event);
      if (context == null) {
        return null;
      }
      SpanContext spanContext = Span.fromContext(context).getSpanContext();
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
