/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmq;

import io.opentelemetry.context.Context;
import org.apache.rocketmq.client.hook.ConsumeMessageContext;
import org.apache.rocketmq.client.hook.ConsumeMessageHook;

final class TracingConsumeMessageHookImpl implements ConsumeMessageHook {

  private final RocketMqConsumerTracer tracer;

  TracingConsumeMessageHookImpl(RocketMqConsumerTracer tracer) {
    this.tracer = tracer;
  }

  @Override
  public String hookName() {
    return "OpenTelemetryConsumeMessageTraceHook";
  }

  @Override
  public void consumeMessageBefore(ConsumeMessageContext context) {
    if (context == null || context.getMsgList() == null || context.getMsgList().isEmpty()) {
      return;
    }
    Context traceContext = tracer.startSpan(Context.current(), context.getMsgList());
    ContextAndScope contextAndScope = new ContextAndScope(traceContext, traceContext.makeCurrent());
    context.setMqTraceContext(contextAndScope);
  }

  @Override
  public void consumeMessageAfter(ConsumeMessageContext context) {
    if (context == null || context.getMsgList() == null || context.getMsgList().isEmpty()) {
      return;
    }
    if (context.getMqTraceContext() instanceof ContextAndScope) {
      ContextAndScope contextAndScope = (ContextAndScope) context.getMqTraceContext();
      contextAndScope.closeScope();
      tracer.end(contextAndScope.getContext());
    }
  }
}
