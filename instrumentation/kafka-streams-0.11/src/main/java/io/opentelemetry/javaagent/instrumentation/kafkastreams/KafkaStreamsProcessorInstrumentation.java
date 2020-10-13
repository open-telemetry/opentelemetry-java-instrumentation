/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkastreams;

import static io.opentelemetry.javaagent.instrumentation.kafkastreams.KafkaStreamsProcessorInstrumentation.SpanScopeHolder.HOLDER;
import static io.opentelemetry.javaagent.instrumentation.kafkastreams.KafkaStreamsTracer.TRACER;
import static io.opentelemetry.trace.TracingContextUtils.currentContextWith;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPackagePrivate;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.instrumentation.api.SpanWithScope;
import io.opentelemetry.javaagent.tooling.Instrumenter;
import io.opentelemetry.trace.Span;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.kafka.streams.processor.internals.StampedRecord;

public class KafkaStreamsProcessorInstrumentation {
  // These two instrumentations work together to apply StreamTask.process.
  // The combination of these is needed because there's no good instrumentation point.

  public static class SpanScopeHolder {
    public static final ThreadLocal<SpanScopeHolder> HOLDER = new ThreadLocal<>();

    private SpanWithScope spanWithScope;

    public SpanWithScope getSpanWithScope() {
      return spanWithScope;
    }

    public void setSpanWithScope(SpanWithScope spanWithScope) {
      this.spanWithScope = spanWithScope;
    }
  }

  @AutoService(Instrumenter.class)
  public static class StartInstrumentation extends Instrumenter.Default {

    public StartInstrumentation() {
      super("kafka", "kafka-streams");
    }

    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
      return named("org.apache.kafka.streams.processor.internals.PartitionGroup");
    }

    @Override
    public String[] helperClassNames() {
      return new String[] {
        packageName + ".KafkaStreamsTracer",
        packageName + ".TextMapExtractAdapter",
        KafkaStreamsProcessorInstrumentation.class.getName() + "$SpanScopeHolder"
      };
    }

    @Override
    public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
      return singletonMap(
          isMethod()
              .and(isPackagePrivate())
              .and(named("nextRecord"))
              .and(returns(named("org.apache.kafka.streams.processor.internals.StampedRecord"))),
          StartInstrumentation.class.getName() + "$StartSpanAdvice");
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

        Span span = TRACER.startSpan(record);

        holder.setSpanWithScope(new SpanWithScope(span, currentContextWith(span)));
      }
    }
  }

  @AutoService(Instrumenter.class)
  public static class StopInstrumentation extends Instrumenter.Default {

    public StopInstrumentation() {
      super("kafka", "kafka-streams");
    }

    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
      return named("org.apache.kafka.streams.processor.internals.StreamTask");
    }

    @Override
    public String[] helperClassNames() {
      return new String[] {
        packageName + ".KafkaStreamsTracer",
        packageName + ".TextMapExtractAdapter",
        KafkaStreamsProcessorInstrumentation.class.getName() + "$SpanScopeHolder"
      };
    }

    @Override
    public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
      return singletonMap(
          isMethod().and(isPublic()).and(named("process")).and(takesArguments(0)),
          StopInstrumentation.class.getName() + "$StopSpanAdvice");
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
        SpanWithScope spanWithScope = holder.getSpanWithScope();
        if (spanWithScope != null) {
          spanWithScope.closeScope();

          Span span = spanWithScope.getSpan();

          if (throwable != null) {
            TRACER.endExceptionally(span, throwable);
          } else {
            TRACER.end(span);
          }
        }
      }
    }
  }
}
