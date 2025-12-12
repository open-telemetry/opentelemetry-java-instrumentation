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

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.api.incubator.log.LoggingContextConstants;
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

      String traceIdKey =
          DeclarativeConfigUtil.getString(
                  GlobalOpenTelemetry.get(), "general", "logging", "trace_id")
              .orElse(LoggingContextConstants.TRACE_ID);
      String spanIdKey =
          DeclarativeConfigUtil.getString(
                  GlobalOpenTelemetry.get(), "general", "logging", "span_id")
              .orElse(LoggingContextConstants.SPAN_ID);
      String traceFlagsKey =
          DeclarativeConfigUtil.getString(
                  GlobalOpenTelemetry.get(), "general", "logging", "trace_flags")
              .orElse(LoggingContextConstants.TRACE_FLAGS);
      boolean traceId = traceIdKey.equals(key);
      boolean spanId = spanIdKey.equals(key);
      boolean traceFlags = traceFlagsKey.equals(key);

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

      String traceIdKey =
          DeclarativeConfigUtil.getString(
                  GlobalOpenTelemetry.get(), "general", "logging", "trace_id")
              .orElse(LoggingContextConstants.TRACE_ID);
      String spanIdKey =
          DeclarativeConfigUtil.getString(
                  GlobalOpenTelemetry.get(), "general", "logging", "span_id")
              .orElse(LoggingContextConstants.SPAN_ID);
      String traceFlagsKey =
          DeclarativeConfigUtil.getString(
                  GlobalOpenTelemetry.get(), "general", "logging", "trace_flags")
              .orElse(LoggingContextConstants.TRACE_FLAGS);

      if (value.containsKey(traceIdKey)
          && value.containsKey(spanIdKey)
          && value.containsKey(traceFlagsKey)) {
        return value;
      }

      SpanContext spanContext = JbossLogManagerHelper.getSpanContext(record);
      if (!spanContext.isValid()) {
        return value;
      }

      if (!value.containsKey(traceIdKey)) {
        value.put(traceIdKey, spanContext.getTraceId());
      }

      if (!value.containsKey(spanIdKey)) {
        value.put(spanIdKey, spanContext.getSpanId());
      }

      if (!value.containsKey(traceFlagsKey)) {
        value.put(traceFlagsKey, spanContext.getTraceFlags().asHex());
      }
      return value;
    }
  }
}
