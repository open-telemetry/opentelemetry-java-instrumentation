package datadog.trace.instrumentation.kafka_clients;

import datadog.trace.agent.decorator.ClientDecorator;
import datadog.trace.api.DDSpanTypes;
import datadog.trace.api.DDTags;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.tag.Tags;
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
          return DDSpanTypes.MESSAGE_PRODUCER;
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
          return DDSpanTypes.MESSAGE_CONSUMER;
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

  public void onConsume(final Scope scope, final ConsumerRecord record) {
    final Span span = scope.span();
    if (record != null) {
      final String topic = record.topic() == null ? "kafka" : record.topic();
      span.setTag(DDTags.RESOURCE_NAME, "Consume Topic " + topic);
      span.setTag("partition", record.partition());
      span.setTag("offset", record.offset());
    }
  }

  public void onProduce(final Scope scope, final ProducerRecord record) {
    if (record != null) {
      final Span span = scope.span();

      final String topic = record.topic() == null ? "kafka" : record.topic();
      if (record.partition() != null) {
        span.setTag("kafka.partition", record.partition());
      }

      span.setTag(DDTags.RESOURCE_NAME, "Produce Topic " + topic);
    }
  }
}
