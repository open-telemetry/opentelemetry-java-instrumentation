/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkastreams;

import static io.opentelemetry.javaagent.instrumentation.kafkastreams.KafkaStreamsTracer.tracer;
import static io.opentelemetry.javaagent.instrumentation.kafkastreams.SpanScopeHolder.HOLDER;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPackagePrivate;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.javaagent.instrumentation.api.SpanWithScope;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.kafka.streams.processor.internals.StampedRecord;

final class StreamTaskStartInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.kafka.streams.processor.internals.PartitionGroup");
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod()
            .and(isPackagePrivate())
            .and(named("nextRecord"))
            .and(returns(named("org.apache.kafka.streams.processor.internals.StampedRecord"))),
        StreamTaskStartInstrumentation.class.getName() + "$StartSpanAdvice");
  }

  public static class StartSpanAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(@Advice.Return StampedRecord record) {
      if (record == null) {
        return;
      }

      SpanScopeHolder holder = HOLDER.get();
      if (holder == null) {
        // somehow nextRecord() was called outside of process()
        return;
      }

      Span span = tracer().startSpan(record);

      holder.setSpanWithScope(new SpanWithScope(span, span.makeCurrent()));
    }
  }
}
