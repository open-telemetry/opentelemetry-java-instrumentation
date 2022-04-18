/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jbosslogmanager.v1_1;

import static io.opentelemetry.instrumentation.api.log.LoggingContextConstants.SPAN_ID;
import static io.opentelemetry.instrumentation.api.log.LoggingContextConstants.TRACE_FLAGS;
import static io.opentelemetry.instrumentation.api.log.LoggingContextConstants.TRACE_ID;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.field.VirtualField;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge;
import java.util.Hashtable;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.jboss.logmanager.ExtLogRecord;
import org.jboss.logmanager.MDC;

public class JbossExtLogRecordInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.jboss.logmanager.ExtLogRecord");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(isPublic())
            .and(named("getMdc"))
            .and(takesArguments(1))
            .and(takesArgument(0, String.class)),
        JbossExtLogRecordInstrumentation.class.getName() + "$GetMdcAdvice");

    transformer.applyAdviceToMethod(
        isMethod().and(isPublic()).and(named("copyMdc")).and(takesArguments(0)),
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
        SpanContext spanContext = Java8BytecodeBridge.spanFromContext(context).getSpanContext();

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

  @SuppressWarnings("unused")
  public static class GetMdcCopyAdvice {

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.This ExtLogRecord record,
        @Advice.FieldValue(value = "mdcCopy", readOnly = false) Map<String, String> mdcCopy) {
      // this advice basically replaces the original method

      Hashtable mdc = new Hashtable(MDC.copy());

      // Assume already instrumented event if traceId is present.
      if (!mdc.containsKey(TRACE_ID)) {
        Span span = VirtualField.find(ExtLogRecord.class, Span.class).get(record);
        if (span != null && span.getSpanContext().isValid()) {
          SpanContext spanContext = span.getSpanContext();
          mdc.put(TRACE_ID, spanContext.getTraceId());
          mdc.put(SPAN_ID, spanContext.getSpanId());
          mdc.put(TRACE_FLAGS, spanContext.getTraceFlags().asHex());
        }
      }

      mdcCopy = mdc;
    }
  }
}
