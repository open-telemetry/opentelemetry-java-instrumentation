/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jbosslogmanager.mdc.v1_1;

import static io.opentelemetry.instrumentation.api.log.LoggingContextConstants.SPAN_ID;
import static io.opentelemetry.instrumentation.api.log.LoggingContextConstants.TRACE_FLAGS;
import static io.opentelemetry.instrumentation.api.log.LoggingContextConstants.TRACE_ID;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
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
      if (TRACE_ID.equals(key) || SPAN_ID.equals(key) || TRACE_FLAGS.equals(key)) {
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

        switch (key) {
          case TRACE_ID:
            value = spanContext.getTraceId();
            break;
          case SPAN_ID:
            value = spanContext.getSpanId();
            break;
          case TRACE_FLAGS:
            value = spanContext.getTraceFlags().asHex();
            break;
          default:
            // do nothing
        }
      }
    }
  }

  public static class GetMdcCopyAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(
        @Advice.This ExtLogRecord record,
        @Advice.Return(readOnly = false) Map<String, String> value) {

      if (value.containsKey(TRACE_ID)
          && value.containsKey(SPAN_ID)
          && value.containsKey(TRACE_FLAGS)) {
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

      if (!value.containsKey(TRACE_ID)) {
        value.put(TRACE_ID, spanContext.getTraceId());
      }

      if (!value.containsKey(SPAN_ID)) {
        value.put(SPAN_ID, spanContext.getSpanId());
      }

      if (!value.containsKey(TRACE_FLAGS)) {
        value.put(TRACE_FLAGS, spanContext.getTraceFlags().asHex());
      }
    }
  }
}
