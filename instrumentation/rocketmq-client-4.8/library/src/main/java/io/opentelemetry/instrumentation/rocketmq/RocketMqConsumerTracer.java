/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmq;

import static io.opentelemetry.api.trace.SpanKind.CONSUMER;
import static io.opentelemetry.instrumentation.rocketmq.TextMapExtractAdapter.GETTER;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.List;
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

  public Context startSpan(Context parentContext, List<MessageExt> msgs) {
    MessageExt msg = msgs.get(0);
    if (msgs.size() == 1) {
      SpanBuilder spanBuilder = startSpanBuilder(msg).setParent(extractParent(msg));
      return withClientSpan(parentContext, spanBuilder.startSpan());
    } else {
      SpanBuilder spanBuilder =
          tracer
              .spanBuilder(msg.getTopic() + " receive")
              .setSpanKind(CONSUMER)
              .setAttribute(SemanticAttributes.MESSAGING_SYSTEM, "rocketmq")
              .setAttribute(SemanticAttributes.MESSAGING_OPERATION, "receive");
      Context rootContext = withClientSpan(parentContext, spanBuilder.startSpan());
      for (MessageExt message : msgs) {
        createChildSpan(rootContext, message);
      }
      return rootContext;
    }
  }

  public void createChildSpan(Context parentContext, MessageExt msg) {
    SpanBuilder childSpanBuilder =
        startSpanBuilder(msg)
            .setParent(parentContext)
            .addLink(Span.fromContext(extractParent(msg)).getSpanContext());
    end(withClientSpan(parentContext, childSpanBuilder.startSpan()));
  }

  public SpanBuilder startSpanBuilder(MessageExt msg) {
    SpanBuilder spanBuilder =
        tracer
            .spanBuilder(spanNameOnConsume(msg))
            .setSpanKind(CONSUMER)
            .setAttribute(SemanticAttributes.MESSAGING_SYSTEM, "rocketmq")
            .setAttribute(SemanticAttributes.MESSAGING_DESTINATION, msg.getTopic())
            .setAttribute(SemanticAttributes.MESSAGING_DESTINATION_KIND, "topic")
            .setAttribute(SemanticAttributes.MESSAGING_OPERATION, "process")
            .setAttribute(SemanticAttributes.MESSAGING_MESSAGE_ID, msg.getMsgId())
            .setAttribute(
                SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES,
                (long) msg.getBody().length);
    onConsume(spanBuilder, msg);
    return spanBuilder;
  }

  private Context extractParent(MessageExt msg) {
    if (RocketMqClientConfig.isPropagationEnabled()) {
      return extract(msg.getProperties(), GETTER);
    } else {
      return Context.current();
    }
  }

  void onConsume(SpanBuilder spanBuilder, MessageExt msg) {
    if (RocketMqClientConfig.CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES) {
      spanBuilder.setAttribute("messaging.rocketmq.tags", msg.getTags());
      spanBuilder.setAttribute("messaging.rocketmq.queue_id", msg.getQueueId());
      spanBuilder.setAttribute("messaging.rocketmq.queue_offset", msg.getQueueOffset());
      spanBuilder.setAttribute("messaging.rocketmq.broker_address", getBrokerHost(msg));
    }
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
}
