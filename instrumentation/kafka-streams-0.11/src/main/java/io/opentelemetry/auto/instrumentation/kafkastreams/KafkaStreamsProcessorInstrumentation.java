/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.auto.instrumentation.kafkastreams;

import static io.opentelemetry.auto.bootstrap.instrumentation.decorator.BaseDecorator.extract;
import static io.opentelemetry.auto.instrumentation.kafkastreams.KafkaStreamsDecorator.CONSUMER_DECORATE;
import static io.opentelemetry.auto.instrumentation.kafkastreams.KafkaStreamsDecorator.TRACER;
import static io.opentelemetry.auto.instrumentation.kafkastreams.KafkaStreamsProcessorInstrumentation.SpanScopeHolder.HOLDER;
import static io.opentelemetry.auto.instrumentation.kafkastreams.TextMapExtractAdapter.GETTER;
import static io.opentelemetry.trace.Span.Kind.CONSUMER;
import static io.opentelemetry.trace.TracingContextUtils.currentContextWith;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPackagePrivate;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import io.opentelemetry.auto.instrumentation.api.SpanWithScope;
import io.opentelemetry.auto.tooling.Instrumenter;
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

    public void setSpanWithScope(final SpanWithScope spanWithScope) {
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
        packageName + ".KafkaStreamsDecorator",
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
      public static void onExit(@Advice.Return final StampedRecord record) {
        if (record == null) {
          return;
        }

        final SpanScopeHolder holder = HOLDER.get();
        if (holder == null) {
          // somehow nextRecord() was called outside of process()
          return;
        }

        final Span.Builder spanBuilder =
            TRACER.spanBuilder(CONSUMER_DECORATE.spanNameForConsume(record)).setSpanKind(CONSUMER);
        spanBuilder.setParent(extract(record.value.headers(), GETTER));
        final Span span = spanBuilder.startSpan();
        CONSUMER_DECORATE.afterStart(span);
        CONSUMER_DECORATE.onConsume(span, record);

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
        packageName + ".KafkaStreamsDecorator",
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
        final SpanScopeHolder holder = new SpanScopeHolder();
        HOLDER.set(holder);
        return holder;
      }

      @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
      public static void stopSpan(
          @Advice.Enter final SpanScopeHolder holder, @Advice.Thrown final Throwable throwable) {
        HOLDER.remove();
        final SpanWithScope spanWithScope = holder.getSpanWithScope();
        if (spanWithScope != null) {
          final Span span = spanWithScope.getSpan();
          CONSUMER_DECORATE.onError(span, throwable);
          CONSUMER_DECORATE.beforeFinish(span);
          span.end();
          spanWithScope.closeScope();
        }
      }
    }
  }
}
