package datadog.trace.instrumentation.kafka_streams;

import datadog.trace.agent.decorator.ClientDecorator;
import datadog.trace.api.DDSpanTypes;
import datadog.trace.api.DDTags;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.tag.Tags;
import org.apache.kafka.streams.processor.internals.StampedRecord;

public class KafkaStreamsDecorator extends ClientDecorator {
  public static final KafkaStreamsDecorator CONSUMER_DECORATE = new KafkaStreamsDecorator();

  @Override
  protected String[] instrumentationNames() {
    return new String[] {"kafka", "kafka-streams"};
  }

  @Override
  protected String service() {
    return "kafka";
  }

  @Override
  protected String component() {
    return "java-kafka";
  }

  @Override
  protected String spanKind() {
    return Tags.SPAN_KIND_CONSUMER;
  }

  @Override
  protected String spanType() {
    return DDSpanTypes.MESSAGE_CONSUMER;
  }

  public void onConsume(final Scope scope, final StampedRecord record) {
    final Span span = scope.span();
    if (record != null) {
      final String topic = record.topic() == null ? "kafka" : record.topic();
      span.setTag(DDTags.RESOURCE_NAME, "Consume Topic " + topic);
      span.setTag("partition", record.partition());
      span.setTag("offset", record.offset());
    }
  }
}
