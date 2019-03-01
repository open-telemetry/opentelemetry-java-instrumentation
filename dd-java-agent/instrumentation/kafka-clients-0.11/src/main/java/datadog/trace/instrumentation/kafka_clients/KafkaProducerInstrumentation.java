package datadog.trace.instrumentation.kafka_clients;

import static datadog.trace.instrumentation.kafka_clients.KafkaDecorator.PRODUCER_DECORATE;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import io.opentracing.Scope;
import io.opentracing.propagation.Format;
import io.opentracing.util.GlobalTracer;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;

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
      "datadog.trace.agent.decorator.BaseDecorator",
      "datadog.trace.agent.decorator.ClientDecorator",
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
        ProducerAdvice.class.getName());
  }

  public static class ProducerAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Scope startSpan(
        @Advice.Argument(value = 0, readOnly = false) ProducerRecord record,
        @Advice.Argument(value = 1, readOnly = false) Callback callback) {
      final Scope scope = GlobalTracer.get().buildSpan("kafka.produce").startActive(false);
      PRODUCER_DECORATE.afterStart(scope);
      PRODUCER_DECORATE.onProduce(scope, record);

      callback = new ProducerCallback(callback, scope);

      try {
        GlobalTracer.get()
            .inject(
                scope.span().context(),
                Format.Builtin.TEXT_MAP,
                new TextMapInjectAdapter(record.headers()));
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

        GlobalTracer.get()
            .inject(
                scope.span().context(),
                Format.Builtin.TEXT_MAP,
                new TextMapInjectAdapter(record.headers()));
      }

      return scope;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Enter final Scope scope, @Advice.Thrown final Throwable throwable) {
      PRODUCER_DECORATE.onError(scope, throwable);
      PRODUCER_DECORATE.beforeFinish(scope);
      scope.close();
    }
  }

  public static class ProducerCallback implements Callback {
    private final Callback callback;
    private final Scope scope;

    public ProducerCallback(final Callback callback, final Scope scope) {
      this.callback = callback;
      this.scope = scope;
    }

    @Override
    public void onCompletion(final RecordMetadata metadata, final Exception exception) {
      PRODUCER_DECORATE.onError(scope, exception);
      try {
        if (callback != null) {
          callback.onCompletion(metadata, exception);
        }
      } finally {
        PRODUCER_DECORATE.beforeFinish(scope);
        scope.span().finish();
        scope.close();
      }
    }
  }
}
