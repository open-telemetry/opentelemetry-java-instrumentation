/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmq;

import static io.opentelemetry.instrumentation.rocketmq.RocketMqProducerTracer.tracer;

import io.opentelemetry.api.trace.Span;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;

public class SendCallbackWrapper implements SendCallback {
  private final SendCallback sendCallback;
  private final Span span;

  public SendCallbackWrapper(SendCallback sendCallback, Span span) {
    this.sendCallback = sendCallback;
    this.span = span;
  }

  @Override
  public void onSuccess(SendResult sendResult) {
    tracer().onCallback(span, sendResult);
    tracer().end(span);
    sendCallback.onSuccess(sendResult);
  }

  @Override
  public void onException(Throwable e) {
    tracer().endExceptionally(span, e);
    sendCallback.onException(e);
  }
}
