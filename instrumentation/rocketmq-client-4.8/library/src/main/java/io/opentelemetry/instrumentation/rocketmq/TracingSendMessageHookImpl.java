/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmq;

import static io.opentelemetry.instrumentation.rocketmq.TextMapInjectAdapter.SETTER;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;
import org.apache.rocketmq.client.hook.SendMessageContext;
import org.apache.rocketmq.client.hook.SendMessageHook;

final class TracingSendMessageHookImpl implements SendMessageHook {

  private final RocketMqProducerTracer tracer;
  private boolean propagationEnabled;

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
    Context traceContext =
        tracer.startProducerSpan(Context.current(), context.getBrokerAddr(), context.getMessage());
    if (propagationEnabled) {
      GlobalOpenTelemetry.getPropagators()
          .getTextMapPropagator()
          .inject(traceContext, context.getMessage().getProperties(), SETTER);
    }
    ContextAndScope contextAndScope = new ContextAndScope(traceContext, traceContext.makeCurrent());
    context.setMqTraceContext(contextAndScope);
  }

  @Override
  public void sendMessageAfter(SendMessageContext context) {
    if (context == null || context.getMqTraceContext() == null || context.getSendResult() == null) {
      return;
    }
    if (context.getMqTraceContext() instanceof ContextAndScope) {
      ContextAndScope contextAndScope = (ContextAndScope) context.getMqTraceContext();
      tracer.afterProduce(contextAndScope.getContext(), context.getSendResult());
      contextAndScope.closeScope();
      tracer.end(contextAndScope.getContext());
    }
  }
}
