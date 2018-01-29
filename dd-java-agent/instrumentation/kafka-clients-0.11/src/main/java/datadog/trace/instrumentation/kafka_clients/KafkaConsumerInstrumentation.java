package datadog.trace.instrumentation.kafka_clients;

import static datadog.trace.agent.tooling.ClassLoaderMatcher.classLoaderHasClasses;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.DDAdvice;
import datadog.trace.agent.tooling.HelperInjector;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.api.DDSpanTypes;
import datadog.trace.api.DDTags;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import java.util.Iterator;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import org.apache.kafka.clients.consumer.ConsumerRecord;

@AutoService(Instrumenter.class)
public final class KafkaConsumerInstrumentation implements Instrumenter {
  public static final HelperInjector HELPER_INJECTOR =
      new HelperInjector(
          "datadog.trace.instrumentation.kafka_clients.TextMapExtractAdapter",
          "datadog.trace.instrumentation.kafka_clients.TracingIterable",
          "datadog.trace.instrumentation.kafka_clients.TracingIterable$TracingIterator",
          "datadog.trace.instrumentation.kafka_clients.TracingIterable$SpanBuilderDecorator",
          "datadog.trace.instrumentation.kafka_clients.KafkaConsumerInstrumentation$ConsumeScopeAction");
  public static final ConsumeScopeAction CONSUME_ACTION = new ConsumeScopeAction();

  private static final String OPERATION = "kafka.consume";
  private static final String COMPONENT_NAME = "java-kafka";

  @Override
  public AgentBuilder instrument(final AgentBuilder agentBuilder) {
    return agentBuilder
        .type(
            named("org.apache.kafka.clients.consumer.ConsumerRecords"),
            classLoaderHasClasses(
                "org.apache.kafka.common.header.Header", "org.apache.kafka.common.header.Headers"))
        .transform(HELPER_INJECTOR)
        .transform(
            DDAdvice.create()
                .advice(
                    isMethod()
                        .and(isPublic())
                        .and(named("records"))
                        .and(takesArgument(0, String.class))
                        .and(returns(Iterable.class)),
                    IterableAdvice.class.getName())
                .advice(
                    isMethod()
                        .and(isPublic())
                        .and(named("iterator"))
                        .and(takesArguments(0))
                        .and(returns(Iterator.class)),
                    IteratorAdvice.class.getName()))
        .asDecorator();
  }

  public static class IterableAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void wrap(@Advice.Return(readOnly = false) Iterable<ConsumerRecord> iterable) {
      iterable = new TracingIterable(iterable, OPERATION, CONSUME_ACTION);
    }
  }

  public static class IteratorAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void wrap(@Advice.Return(readOnly = false) Iterator<ConsumerRecord> iterator) {
      iterator = new TracingIterable.TracingIterator(iterator, OPERATION, CONSUME_ACTION);
    }
  }

  public static class ConsumeScopeAction
      implements TracingIterable.SpanBuilderDecorator<ConsumerRecord> {

    @Override
    public void decorate(final Tracer.SpanBuilder spanBuilder, final ConsumerRecord record) {
      final String topic = record.topic() == null ? "unknown" : record.topic();
      final SpanContext spanContext =
          GlobalTracer.get()
              .extract(Format.Builtin.TEXT_MAP, new TextMapExtractAdapter(record.headers()));
      spanBuilder
          .asChildOf(spanContext)
          .withTag(DDTags.SERVICE_NAME, "kafka")
          .withTag(DDTags.RESOURCE_NAME, "Consume Topic " + topic)
          .withTag(DDTags.SPAN_TYPE, DDSpanTypes.MESSAGE_CONSUMER)
          .withTag(Tags.COMPONENT.getKey(), COMPONENT_NAME)
          .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CONSUMER)
          .withTag("partition", record.partition())
          .withTag("offset", record.offset());
    }
  }
}
