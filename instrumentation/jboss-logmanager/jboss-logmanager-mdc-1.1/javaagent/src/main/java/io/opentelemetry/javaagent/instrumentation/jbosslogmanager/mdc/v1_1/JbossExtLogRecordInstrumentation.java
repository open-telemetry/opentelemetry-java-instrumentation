/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jbosslogmanager.mdc.v1_1;

import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.Map;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.jboss.logmanager.ExtLogRecord;

public class JbossExtLogRecordInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.jboss.logmanager.ExtLogRecord");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    // available since jboss-logmanager 1.1
    transformer.applyAdviceToMethod(
        isMethod()
            .and(isPublic())
            .and(named("getMdc"))
            .and(takesArguments(1))
            .and(takesArgument(0, String.class)),
        JbossExtLogRecordInstrumentation.class.getName() + "$GetMdcAdvice");

    // available since jboss-logmanager 1.3
    transformer.applyAdviceToMethod(
        isMethod().and(isPublic()).and(takesArguments(0)).and(named("getMdcCopy")),
        JbossExtLogRecordInstrumentation.class.getName() + "$GetMdcCopyAdvice");
  }

  @SuppressWarnings("unused")
  public static class GetMdcAdvice {

    @Nullable
    @AssignReturned.ToReturned
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static String onExit(
        @Advice.This ExtLogRecord record,
        @Advice.Argument(0) String key,
        @Advice.Return @Nullable String value) {

      boolean traceId = AgentCommonConfig.get().getTraceIdKey().equals(key);
      boolean spanId = AgentCommonConfig.get().getSpanIdKey().equals(key);
      boolean traceFlags = AgentCommonConfig.get().getTraceFlagsKey().equals(key);

      if (!traceId && !spanId && !traceFlags) {
        return value;
      }
      if (value != null) {
        // Assume already instrumented event if traceId/spanId/sampled is present.
        return value;
      }

      SpanContext spanContext = JbossLogManagerHelper.getSpanContext(record);
      if (!spanContext.isValid()) {
        return value;
      }

      if (traceId) {
        return spanContext.getTraceId();
      }
      if (spanId) {
        return spanContext.getSpanId();
      }
      // traceFlags == true
      return spanContext.getTraceFlags().asHex();
    }
  }

  @SuppressWarnings("unused")
  public static class GetMdcCopyAdvice {

    @AssignReturned.ToReturned
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static Map<String, String> onExit(
        @Advice.This ExtLogRecord record, @Advice.Return Map<String, String> value) {

      if (value.containsKey(AgentCommonConfig.get().getTraceIdKey())
          && value.containsKey(AgentCommonConfig.get().getSpanIdKey())
          && value.containsKey(AgentCommonConfig.get().getTraceFlagsKey())) {
        return value;
      }

      SpanContext spanContext = JbossLogManagerHelper.getSpanContext(record);
      if (!spanContext.isValid()) {
        return value;
      }

      if (!value.containsKey(AgentCommonConfig.get().getTraceIdKey())) {
        value.put(AgentCommonConfig.get().getTraceIdKey(), spanContext.getTraceId());
      }

      if (!value.containsKey(AgentCommonConfig.get().getSpanIdKey())) {
        value.put(AgentCommonConfig.get().getSpanIdKey(), spanContext.getSpanId());
      }

      if (!value.containsKey(AgentCommonConfig.get().getTraceFlagsKey())) {
        value.put(AgentCommonConfig.get().getTraceFlagsKey(), spanContext.getTraceFlags().asHex());
      }
      return value;
    }
  }
}
