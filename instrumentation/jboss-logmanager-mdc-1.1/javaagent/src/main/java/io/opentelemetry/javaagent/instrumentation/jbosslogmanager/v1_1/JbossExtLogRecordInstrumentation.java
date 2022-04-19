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

import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.field.VirtualField;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge;
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
    transformer.applyAdviceToMethod(
        isMethod()
            .and(isPublic())
            .and(named("getMdc"))
            .and(takesArguments(1))
            .and(takesArgument(0, String.class)),
        JbossExtLogRecordInstrumentation.class.getName() + "$GetMdcAdvice");
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
}
