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

package io.opentelemetry.instrumentation.auto.kafkaclients;

import static io.opentelemetry.instrumentation.auto.kafkaclients.KafkaDecorator.DECORATE;
import static io.opentelemetry.instrumentation.auto.kafkaclients.KafkaDecorator.TRACER;
import static io.opentelemetry.instrumentation.auto.kafkaclients.TextMapInjectAdapter.SETTER;
import static io.opentelemetry.trace.Span.Kind.PRODUCER;
import static io.opentelemetry.trace.TracingContextUtils.currentContextWith;
import static io.opentelemetry.trace.TracingContextUtils.withSpan;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import io.grpc.Context;
import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.instrumentation.auto.api.SpanWithScope;
import io.opentelemetry.javaagent.tooling.Instrumenter;
import io.opentelemetry.trace.Span;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.kafka.clients.ApiVersions;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.record.RecordBatch;

@AutoService(Instrumenter.class)
public final class KafkaProducerInstrumentation extends Instrumenter.Default {

  public KafkaProducerInstrumentation() {
    super("kafka");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.kafka.clients.producer.KafkaProducer");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".KafkaDecorator",
      packageName + ".TextMapInjectAdapter",
      KafkaProducerInstrumentation.class.getName() + "$ProducerCallback"
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod()
            .and(isPublic())
            .and(named("send"))
            .and(takesArgument(0, named("org.apache.kafka.clients.producer.ProducerRecord")))
            .and(takesArgument(1, named("org.apache.kafka.clients.producer.Callback"))),
        KafkaProducerInstrumentation.class.getName() + "$ProducerAdvice");
  }

  public static class ProducerAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static SpanWithScope onEnter(
        @Advice.FieldValue("apiVersions") ApiVersions apiVersions,
        @Advice.Argument(value = 0, readOnly = false) ProducerRecord record,
        @Advice.Argument(value = 1, readOnly = false) Callback callback) {
      Span span =
          TRACER.spanBuilder(DECORATE.spanNameOnProduce(record)).setSpanKind(PRODUCER).startSpan();
      DECORATE.afterStart(span);
      DECORATE.onProduce(span, record);

      callback = new ProducerCallback(callback, span);

      boolean isTombstone = record.value() == null && !record.headers().iterator().hasNext();
      if (isTombstone) {
        span.setAttribute("tombstone", true);
      }

      // Do not inject headers for batch versions below 2
      // This is how similar check is being done in Kafka client itself:
      // https://github.com/apache/kafka/blob/05fcfde8f69b0349216553f711fdfc3f0259c601/clients/src/main/java/org/apache/kafka/common/record/MemoryRecordsBuilder.java#L411-L412
      // Also, do not inject headers if specified by JVM option or environment variable
      // This can help in mixed client environments where clients < 0.11 that do not support
      // headers attempt to read messages that were produced by clients > 0.11 and the magic
      // value of the broker(s) is >= 2
      if (apiVersions.maxUsableProduceMagic() >= RecordBatch.MAGIC_VALUE_V2
          && Config.get().isKafkaClientPropagationEnabled()
          // Must not interfere with tombstones
          && !isTombstone) {
        Context context = withSpan(span, Context.current());
        try {
          OpenTelemetry.getPropagators()
              .getHttpTextFormat()
              .inject(context, record.headers(), SETTER);
        } catch (IllegalStateException e) {
          // headers must be read-only from reused record. try again with new one.
          record =
              new ProducerRecord<>(
                  record.topic(),
                  record.partition(),
                  record.timestamp(),
                  record.key(),
                  record.value(),
                  record.headers());

          OpenTelemetry.getPropagators()
              .getHttpTextFormat()
              .inject(context, record.headers(), SETTER);
        }
      }

      return new SpanWithScope(span, currentContextWith(span));
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Enter SpanWithScope spanWithScope, @Advice.Thrown Throwable throwable) {
      Span span = spanWithScope.getSpan();
      DECORATE.onError(span, throwable);
      DECORATE.beforeFinish(span);
      spanWithScope.closeScope();
      // span finished by ProducerCallback
    }
  }

  public static class ProducerCallback implements Callback {
    private final Callback callback;
    private final Span span;

    public ProducerCallback(Callback callback, Span span) {
      this.callback = callback;
      this.span = span;
    }

    @Override
    public void onCompletion(RecordMetadata metadata, Exception exception) {
      try (Scope scope = currentContextWith(span)) {
        DECORATE.onError(span, exception);
        try {
          if (callback != null) {
            callback.onCompletion(metadata, exception);
          }
        } finally {
          DECORATE.beforeFinish(span);
          span.end();
        }
      }
    }
  }
}
