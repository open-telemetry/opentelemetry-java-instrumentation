/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmq;

import static io.opentelemetry.instrumentation.rocketmq.MapSetter.INSTANCE;

import io.opentelemetry.context.Context;
import org.apache.rocketmq.client.hook.SendMessageContext;
import org.apache.rocketmq.client.hook.SendMessageHook;

final class TracingSendMessageHookImpl implements SendMessageHook {

  private final RocketMqProducerTracer tracer;
  private final boolean propagationEnabled;

  TracingSendMessageHookImpl(RocketMqProducerTracer tracer, boolean propagationEnabled) {
    this.tracer = tracer;
    this.propagationEnabled = propagationEnabled;
  }

  @Override
  public String hookName() {
    return "OpenTelemetrySendMessageTraceHook";
  }

  @Override
  public void sendMessageBefore(SendMessageContext context) {
    if (context == null) {
      return;
    }
    Context otelContext =
        tracer.startProducerSpan(Context.current(), context.getBrokerAddr(), context.getMessage());
    if (propagationEnabled) {
      tracer.inject(otelContext, context.getMessage().getProperties(), INSTANCE);
    }
    context.setMqTraceContext(otelContext);
  }

  @Override
  public void sendMessageAfter(SendMessageContext context) {
    if (context == null || context.getMqTraceContext() == null || context.getSendResult() == null) {
      return;
    }
    if (context.getMqTraceContext() instanceof Context) {
      Context otelContext = (Context) context.getMqTraceContext();
      tracer.afterProduce(otelContext, context.getSendResult());
      tracer.end(otelContext);
    }
  }
}
