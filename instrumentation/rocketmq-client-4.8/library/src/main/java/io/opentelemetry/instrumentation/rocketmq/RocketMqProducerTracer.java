/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmq;

import static io.opentelemetry.api.trace.SpanKind.PRODUCER;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;

final class RocketMqProducerTracer extends BaseTracer {

  private final boolean captureExperimentalSpanAttributes;

  RocketMqProducerTracer(OpenTelemetry openTelemetry, boolean captureExperimentalSpanAttributes) {
    super(openTelemetry);
    this.captureExperimentalSpanAttributes = captureExperimentalSpanAttributes;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.rocketmq-client";
  }

  Context startProducerSpan(Context parentContext, String addr, Message msg) {
    SpanBuilder spanBuilder = spanBuilder(parentContext, spanNameOnProduce(msg), PRODUCER);
    onProduce(spanBuilder, msg, addr);
    return parentContext.with(spanBuilder.startSpan());
  }

  private void onProduce(SpanBuilder spanBuilder, Message msg, String addr) {
    spanBuilder.setAttribute(SemanticAttributes.MESSAGING_SYSTEM, "rocketmq");
    spanBuilder.setAttribute(SemanticAttributes.MESSAGING_DESTINATION_KIND, "topic");
    spanBuilder.setAttribute(SemanticAttributes.MESSAGING_DESTINATION, msg.getTopic());
    if (captureExperimentalSpanAttributes) {
      spanBuilder.setAttribute("messaging.rocketmq.tags", msg.getTags());
      spanBuilder.setAttribute("messaging.rocketmq.broker_address", addr);
    }
  }

  public void afterProduce(Context context, SendResult sendResult) {
    Span span = Span.fromContext(context);
    span.setAttribute(SemanticAttributes.MESSAGING_MESSAGE_ID, sendResult.getMsgId());
    if (captureExperimentalSpanAttributes) {
      span.setAttribute("messaging.rocketmq.send_result", sendResult.getSendStatus().name());
    }
  }

  private static String spanNameOnProduce(Message msg) {
    return msg.getTopic() + " send";
  }
}
