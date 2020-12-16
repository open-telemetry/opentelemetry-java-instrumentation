/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkastreams;

import static io.opentelemetry.javaagent.instrumentation.kafkastreams.KafkaStreamsTracer.tracer;
import static io.opentelemetry.javaagent.instrumentation.kafkastreams.SpanScopeHolder.HOLDER;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class StreamTaskStopInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.kafka.streams.processor.internals.StreamTask");
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod().and(isPublic()).and(named("process")).and(takesArguments(0)),
        StreamTaskStopInstrumentation.class.getName() + "$StopSpanAdvice");
  }

  public static class StopSpanAdvice {

    @Advice.OnMethodEnter
    public static SpanScopeHolder onEnter() {
      SpanScopeHolder holder = new SpanScopeHolder();
      HOLDER.set(holder);
      return holder;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Enter SpanScopeHolder holder, @Advice.Thrown Throwable throwable) {
      HOLDER.remove();
      Span span = holder.getSpan();
      if (span != null) {
        holder.closeScope();

        if (throwable != null) {
          tracer().endExceptionally(span, throwable);
        } else {
          tracer().end(span);
        }
      }
    }
  }
}
