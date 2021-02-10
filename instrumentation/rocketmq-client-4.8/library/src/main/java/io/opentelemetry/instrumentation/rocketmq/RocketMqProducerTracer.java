/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmq;
import static io.opentelemetry.api.trace.SpanKind.PRODUCER;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;

public class RocketMqProducerTracer extends BaseTracer {

  private static final RocketMqProducerTracer TRACER = new RocketMqProducerTracer();

  public static RocketMqProducerTracer tracer() {
    return TRACER;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.rocketmq-client";
  }

  public Span startProducerSpan(String addr, Message msg) {
    Span span = startSpan(spanNameOnProduce(msg), PRODUCER);
    onProduce(span, msg, addr);
    return span;
  }

  public void onCallback(Span span, SendResult sendResult) {
    span.setAttribute("messaging.rocketmq.callback_result", sendResult.getSendStatus().name());
  }

  public void onProduce(Span span, Message msg, String addr) {
    span.setAttribute(SemanticAttributes.MESSAGING_SYSTEM, "rocketmq");
    span.setAttribute(SemanticAttributes.MESSAGING_DESTINATION_KIND, "topic");
    span.setAttribute(SemanticAttributes.MESSAGING_DESTINATION, msg.getTopic());
    span.setAttribute("messaging.rocketmq.tags", msg.getTags());
    span.setAttribute("messaging.rocketmq.broker_address", addr);
  }

  public String spanNameOnProduce(Message msg) {
    return msg.getTopic() + " send";
  }
}
