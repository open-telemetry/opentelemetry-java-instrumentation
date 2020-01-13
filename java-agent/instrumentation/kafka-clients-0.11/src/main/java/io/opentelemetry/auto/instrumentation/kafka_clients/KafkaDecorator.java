package io.opentelemetry.auto.instrumentation.kafka_clients;

import io.opentelemetry.auto.api.MoreTags;
import io.opentelemetry.auto.api.SpanTypes;
import io.opentelemetry.auto.decorator.ClientDecorator;
import io.opentelemetry.auto.instrumentation.api.AgentSpan;
import io.opentelemetry.auto.instrumentation.api.Tags;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;

public abstract class KafkaDecorator extends ClientDecorator {
  public static final KafkaDecorator PRODUCER_DECORATE =
      new KafkaDecorator() {
        @Override
        protected String spanKind() {
          return Tags.SPAN_KIND_PRODUCER;
        }

        @Override
        protected String spanType() {
          return SpanTypes.MESSAGE_PRODUCER;
        }
      };

  public static final KafkaDecorator CONSUMER_DECORATE =
      new KafkaDecorator() {
        @Override
        protected String spanKind() {
          return Tags.SPAN_KIND_CONSUMER;
        }

        @Override
        protected String spanType() {
          return SpanTypes.MESSAGE_CONSUMER;
        }
      };

  @Override
  protected String[] instrumentationNames() {
    return new String[] {"kafka"};
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
  protected abstract String spanKind();

  public void onConsume(final AgentSpan span, final ConsumerRecord record) {
    if (record != null) {
      final String topic = record.topic() == null ? "kafka" : record.topic();
      span.setAttribute(MoreTags.RESOURCE_NAME, "Consume Topic " + topic);
      span.setAttribute("partition", record.partition());
      span.setAttribute("offset", record.offset());
    }
  }

  public void onProduce(final AgentSpan span, final ProducerRecord record) {
    if (record != null) {

      final String topic = record.topic() == null ? "kafka" : record.topic();
      if (record.partition() != null) {
        span.setAttribute("kafka.partition", record.partition());
      }

      span.setAttribute(MoreTags.RESOURCE_NAME, "Produce Topic " + topic);
    }
  }
}
