/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.logback.mdc.v1_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.instrumentation.logback.mdc.v1_0.LogbackSingletons.spanIdKey;
import static io.opentelemetry.javaagent.instrumentation.logback.mdc.v1_0.LogbackSingletons.traceFlagsKey;
import static io.opentelemetry.javaagent.instrumentation.logback.mdc.v1_0.LogbackSingletons.traceIdKey;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import ch.qos.logback.classic.spi.ILoggingEvent;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.baggage.BaggageEntry;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.bootstrap.internal.ConfiguredResourceAttributesHolder;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.HashMap;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner.Typing;
import net.bytebuddy.matcher.ElementMatcher;

public class LoggingEventInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("ch.qos.logback.classic.spi.ILoggingEvent");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("ch.qos.logback.classic.spi.ILoggingEvent"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(isPublic())
            .and(namedOneOf("getMDCPropertyMap", "getMdc"))
            .and(takesArguments(0)),
        LoggingEventInstrumentation.class.getName() + "$GetMdcAdvice");
  }

  @SuppressWarnings("unused")
  public static class GetMdcAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(
        @Advice.This ILoggingEvent event,
        @Advice.Return(typing = Typing.DYNAMIC, readOnly = false) Map<String, String> contextData) {

      if (contextData != null && contextData.containsKey(AgentCommonConfig.get().getTraceIdKey())) {
        // Assume already instrumented event if traceId is present.
        return;
      }

      Context context = VirtualField.find(ILoggingEvent.class, Context.class).get(event);
      if (context == null) {
        return;
      }

      Map<String, String> spanContextData = new HashMap<>();
      if (contextData != null) {
        spanContextData.putAll(contextData);
      }

      SpanContext spanContext = Java8BytecodeBridge.spanFromContext(context).getSpanContext();

      if (spanContext.isValid()) {
        spanContextData.put(traceIdKey(), spanContext.getTraceId());
        spanContextData.put(spanIdKey(), spanContext.getSpanId());
        spanContextData.put(traceFlagsKey(), spanContext.getTraceFlags().asHex());
      }
      spanContextData.putAll(ConfiguredResourceAttributesHolder.getResourceAttributes());

      if (LogbackSingletons.addBaggage()) {
        Baggage baggage = Java8BytecodeBridge.baggageFromContext(context);

        // using a lambda here does not play nicely with instrumentation bytecode process
        // (Java 6 related errors are observed) so relying on for loop instead
        for (Map.Entry<String, BaggageEntry> entry : baggage.asMap().entrySet()) {
          spanContextData.put(
              // prefix all baggage values to avoid clashes with existing context
              "baggage." + entry.getKey(), entry.getValue().getValue());
        }
      }

      contextData = spanContextData;
    }
  }
}
