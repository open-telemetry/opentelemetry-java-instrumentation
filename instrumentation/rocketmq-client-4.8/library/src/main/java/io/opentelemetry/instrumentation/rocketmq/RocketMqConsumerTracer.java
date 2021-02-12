/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmq;

import static io.opentelemetry.api.trace.SpanKind.CONSUMER;
import static io.opentelemetry.instrumentation.rocketmq.TextMapExtractAdapter.GETTER;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.List;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.ConsumeOrderlyStatus;
import org.apache.rocketmq.common.message.MessageExt;

public class RocketMqConsumerTracer extends BaseTracer {

  private static final RocketMqConsumerTracer TRACER = new RocketMqConsumerTracer();

  public static RocketMqConsumerTracer tracer() {
    return TRACER;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.rocketmq-client";
  }

  public Span startSpan(List<MessageExt> msgs) {
    MessageExt msg = msgs.get(0);
    Span span =
        tracer
            .spanBuilder(spanNameOnConsume(msg))
            .setSpanKind(CONSUMER)
            .setParent(extractParent(msg))
            .setAttribute(SemanticAttributes.MESSAGING_SYSTEM, "rocketmq")
            .setAttribute(SemanticAttributes.MESSAGING_DESTINATION, msg.getTopic())
            .setAttribute(SemanticAttributes.MESSAGING_DESTINATION_KIND, "topic")
            .setAttribute(SemanticAttributes.MESSAGING_OPERATION, "process")
            .setAttribute(
                SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES, getStoreSize(msgs))
            .startSpan();
    onConsume(span, msg);
    return span;
  }

  private Context extractParent(MessageExt msg) {
    if (RocketMqClientConfig.isPropagationEnabled()) {
      return extract(msg.getProperties(), GETTER);
    } else {
      return Context.current();
    }
  }

  void onConsume(Span span, MessageExt msg) {
    span.setAttribute("messaging.rocketmq.tags", msg.getTags());
    span.setAttribute("messaging.rocketmq.queue_id", msg.getQueueId());
    span.setAttribute("messaging.rocketmq.queue_offset", msg.getQueueOffset());
    span.setAttribute("messaging.rocketmq.broker_address", getBrokerHost(msg));
  }

  long getStoreSize(List<MessageExt> msgs) {
    long storeSize = msgs.stream().mapToInt(item -> item.getStoreSize()).sum();
    return storeSize;
  }

  String spanNameOnConsume(MessageExt msg) {
    return msg.getTopic() + " process";
  }

  String getBrokerHost(MessageExt msg) {
    if (msg.getStoreHost() != null) {
      return msg.getStoreHost().toString().replace("/", "");
    } else {
      return null;
    }
  }

  public void endConcurrentlySpan(Span span, ConsumeConcurrentlyStatus status) {
    span.setAttribute("messaging.rocketmq.consume_concurrently_status", status.name());
  }

  public void endOrderlySpan(Span span, ConsumeOrderlyStatus status) {
    span.setAttribute("messaging.rocketmq.consume_orderly_status", status.name());
  }
}
