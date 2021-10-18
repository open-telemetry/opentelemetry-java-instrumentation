/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmq;

import static io.opentelemetry.api.trace.SpanKind.CONSUMER;
import static io.opentelemetry.instrumentation.rocketmq.TextMapExtractAdapter.GETTER;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.rocketmq.common.message.MessageExt;

final class RocketMqConsumerTracer extends BaseTracer {

  private final boolean captureExperimentalSpanAttributes;
  private final boolean propagationEnabled;

  RocketMqConsumerTracer(
      OpenTelemetry openTelemetry,
      boolean captureExperimentalSpanAttributes,
      boolean propagationEnabled) {
    super(openTelemetry);
    this.captureExperimentalSpanAttributes = captureExperimentalSpanAttributes;
    this.propagationEnabled = propagationEnabled;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.rocketmq-client-4.8";
  }

  Context startSpan(Context parentContext, List<MessageExt> msgs) {
    if (msgs.size() == 1) {
      SpanBuilder spanBuilder = startSpanBuilder(extractParent(msgs.get(0)), msgs.get(0));
      return withConsumerSpan(parentContext, spanBuilder.startSpan());
    } else {
      SpanBuilder spanBuilder =
          spanBuilder(parentContext, "multiple_sources receive", CONSUMER)
              .setAttribute(SemanticAttributes.MESSAGING_SYSTEM, "rocketmq")
              .setAttribute(SemanticAttributes.MESSAGING_OPERATION, "receive");
      Context rootContext = withConsumerSpan(parentContext, spanBuilder.startSpan());
      for (MessageExt message : msgs) {
        createChildSpan(rootContext, message);
      }
      return rootContext;
    }
  }

  private void createChildSpan(Context parentContext, MessageExt msg) {
    SpanBuilder childSpanBuilder =
        startSpanBuilder(parentContext, msg)
            .addLink(Span.fromContext(extractParent(msg)).getSpanContext());
    end(parentContext.with(childSpanBuilder.startSpan()));
  }

  private SpanBuilder startSpanBuilder(Context parentContext, MessageExt msg) {
    SpanBuilder spanBuilder =
        spanBuilder(parentContext, spanNameOnConsume(msg), CONSUMER)
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
    if (propagationEnabled) {
      return extract(msg.getProperties(), GETTER);
    } else {
      return Context.current();
    }
  }

  private void onConsume(SpanBuilder spanBuilder, MessageExt msg) {
    if (captureExperimentalSpanAttributes) {
      spanBuilder.setAttribute("messaging.rocketmq.tags", msg.getTags());
      spanBuilder.setAttribute("messaging.rocketmq.queue_id", msg.getQueueId());
      spanBuilder.setAttribute("messaging.rocketmq.queue_offset", msg.getQueueOffset());
      spanBuilder.setAttribute("messaging.rocketmq.broker_address", getBrokerHost(msg));
    }
  }

  private static String spanNameOnConsume(MessageExt msg) {
    return msg.getTopic() + " process";
  }

  @Nullable
  private static String getBrokerHost(MessageExt msg) {
    if (msg.getStoreHost() != null) {
      return msg.getStoreHost().toString().replace("/", "");
    } else {
      return null;
    }
  }
}
