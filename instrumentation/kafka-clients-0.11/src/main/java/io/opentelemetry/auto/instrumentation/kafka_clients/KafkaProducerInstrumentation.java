package io.opentelemetry.auto.instrumentation.kafka_clients;

import static io.opentelemetry.auto.instrumentation.kafka_clients.KafkaDecorator.PRODUCER_DECORATE;
import static io.opentelemetry.auto.instrumentation.kafka_clients.KafkaDecorator.TRACER;
import static io.opentelemetry.auto.instrumentation.kafka_clients.TextMapInjectAdapter.SETTER;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import io.opentelemetry.auto.instrumentation.api.SpanScopePair;
import io.opentelemetry.auto.tooling.Instrumenter;
import io.opentelemetry.context.Scope;
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
      "io.opentelemetry.auto.decorator.BaseDecorator",
      "io.opentelemetry.auto.decorator.ClientDecorator",
      packageName + ".KafkaDecorator",
      packageName + ".KafkaDecorator$1",
      packageName + ".KafkaDecorator$2",
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
    public static SpanScopePair onEnter(
        @Advice.FieldValue("apiVersions") final ApiVersions apiVersions,
        @Advice.Argument(value = 0, readOnly = false) ProducerRecord record,
        @Advice.Argument(value = 1, readOnly = false) Callback callback) {
      final Span span = TRACER.spanBuilder("kafka.produce").startSpan();
      PRODUCER_DECORATE.afterStart(span);
      PRODUCER_DECORATE.onProduce(span, record);

      callback = new ProducerCallback(callback, span);

      // Do not inject headers for batch versions below 2
      // This is how similar check is being done in Kafka client itself:
      // https://github.com/apache/kafka/blob/05fcfde8f69b0349216553f711fdfc3f0259c601/clients/src/main/java/org/apache/kafka/common/record/MemoryRecordsBuilder.java#L411-L412
      if (apiVersions.maxUsableProduceMagic() >= RecordBatch.MAGIC_VALUE_V2) {
        try {
          TRACER.getHttpTextFormat().inject(span.getContext(), record.headers(), SETTER);
        } catch (final IllegalStateException e) {
          // headers must be read-only from reused record. try again with new one.
          record =
              new ProducerRecord<>(
                  record.topic(),
                  record.partition(),
                  record.timestamp(),
                  record.key(),
                  record.value(),
                  record.headers());

          TRACER.getHttpTextFormat().inject(span.getContext(), record.headers(), SETTER);
        }
      }

      return new SpanScopePair(span, TRACER.withSpan(span));
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Enter final SpanScopePair spanScopePair, @Advice.Thrown final Throwable throwable) {
      final Span span = spanScopePair.getSpan();
      PRODUCER_DECORATE.onError(span, throwable);
      PRODUCER_DECORATE.beforeFinish(span);
      span.end();
      spanScopePair.getScope().close();
    }
  }

  public static class ProducerCallback implements Callback {
    private final Callback callback;
    private final Span span;

    public ProducerCallback(final Callback callback, final Span span) {
      this.callback = callback;
      this.span = span;
    }

    @Override
    public void onCompletion(final RecordMetadata metadata, final Exception exception) {
      try (final Scope scope = TRACER.withSpan(span)) {
        PRODUCER_DECORATE.onError(span, exception);
        try {
          if (callback != null) {
            callback.onCompletion(metadata, exception);
          }
        } finally {
          PRODUCER_DECORATE.beforeFinish(span);
          span.end();
        }
      }
    }
  }
}
