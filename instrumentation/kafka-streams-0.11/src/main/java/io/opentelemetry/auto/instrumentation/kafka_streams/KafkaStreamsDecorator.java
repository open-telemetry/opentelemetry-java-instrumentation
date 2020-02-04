package io.opentelemetry.auto.instrumentation.kafka_streams;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.decorator.ClientDecorator;
import io.opentelemetry.auto.instrumentation.api.MoreTags;
import io.opentelemetry.auto.instrumentation.api.SpanTypes;
import io.opentelemetry.auto.instrumentation.api.Tags;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;
import org.apache.kafka.streams.processor.internals.StampedRecord;

public class KafkaStreamsDecorator extends ClientDecorator {
  public static final KafkaStreamsDecorator CONSUMER_DECORATE = new KafkaStreamsDecorator();

  public static final Tracer TRACER = OpenTelemetry.getTracerFactory().get("io.opentelemetry.auto");

  @Override
  protected String service() {
    return "kafka";
  }

  @Override
  protected String getComponentName() {
    return "java-kafka";
  }

  @Override
  protected String spanKind() {
    return Tags.SPAN_KIND_CONSUMER;
  }

  @Override
  protected String getSpanType() {
    return SpanTypes.MESSAGE_CONSUMER;
  }

  public void onConsume(final Span span, final StampedRecord record) {
    if (record != null) {
      final String topic = record.topic() == null ? "kafka" : record.topic();
      span.setAttribute(MoreTags.RESOURCE_NAME, "Consume Topic " + topic);
      span.setAttribute("partition", record.partition());
      span.setAttribute("offset", record.offset());
    }
  }
}
