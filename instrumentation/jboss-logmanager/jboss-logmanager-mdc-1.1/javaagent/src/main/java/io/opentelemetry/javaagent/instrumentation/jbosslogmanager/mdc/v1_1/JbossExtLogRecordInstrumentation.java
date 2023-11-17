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
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.bootstrap.internal.CommonConfig;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.Map;
import net.bytebuddy.asm.Advice;
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

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(
        @Advice.This ExtLogRecord record,
        @Advice.Argument(0) String key,
        @Advice.Return(readOnly = false) String value) {
      if (CommonConfig.get().getLoggingKeysTraceId().equals(key)
          || CommonConfig.get().getLoggingKeysSpanId().equals(key)
          || CommonConfig.get().getLoggingKeysTraceFlags().equals(key)) {
        if (value != null) {
          // Assume already instrumented event if traceId/spanId/sampled is present.
          return;
        }

        Context context = VirtualField.find(ExtLogRecord.class, Context.class).get(record);
        if (context == null) {
          return;
        }
        SpanContext spanContext = Java8BytecodeBridge.spanFromContext(context).getSpanContext();
        if (!spanContext.isValid()) {
          return;
        }

        if (CommonConfig.get().getLoggingKeysTraceId().equals(key)) {
          value = spanContext.getTraceId();
        }
        if (CommonConfig.get().getLoggingKeysSpanId().equals(key)) {
          value = spanContext.getSpanId();
        }
        if (CommonConfig.get().getLoggingKeysTraceFlags().equals(key)) {
          value = spanContext.getTraceFlags().asHex();
        }
      }
    }
  }

  @SuppressWarnings("unused")
  public static class GetMdcCopyAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(
        @Advice.This ExtLogRecord record,
        @Advice.Return(readOnly = false) Map<String, String> value) {

      if (value.containsKey(CommonConfig.get().getLoggingKeysTraceId())
          && value.containsKey(CommonConfig.get().getLoggingKeysSpanId())
          && value.containsKey(CommonConfig.get().getLoggingKeysTraceFlags())) {
        return;
      }

      Context context = VirtualField.find(ExtLogRecord.class, Context.class).get(record);
      if (context == null) {
        return;
      }

      SpanContext spanContext = Java8BytecodeBridge.spanFromContext(context).getSpanContext();
      if (!spanContext.isValid()) {
        return;
      }

      if (!value.containsKey(CommonConfig.get().getLoggingKeysTraceId())) {
        value.put(CommonConfig.get().getLoggingKeysTraceId(), spanContext.getTraceId());
      }

      if (!value.containsKey(CommonConfig.get().getLoggingKeysSpanId())) {
        value.put(CommonConfig.get().getLoggingKeysSpanId(), spanContext.getSpanId());
      }

      if (!value.containsKey(CommonConfig.get().getLoggingKeysTraceFlags())) {
        value.put(
            CommonConfig.get().getLoggingKeysTraceFlags(), spanContext.getTraceFlags().asHex());
      }
    }
  }
}
