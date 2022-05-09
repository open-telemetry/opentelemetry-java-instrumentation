/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmq;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.context.Context;
import org.apache.rocketmq.client.hook.ConsumeMessageContext;
import org.apache.rocketmq.client.hook.ConsumeMessageHook;

final class TracingConsumeMessageHookImpl implements ConsumeMessageHook {

  private final RocketMqConsumerInstrumenter instrumenter;

  TracingConsumeMessageHookImpl(RocketMqConsumerInstrumenter instrumenter) {
    this.instrumenter = instrumenter;
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
    Context parentContext = Context.current();

    String consumerGroup = context.getConsumerGroup();
    Baggage baggage = Baggage.fromContext(parentContext);
    Baggage newBaggage = baggage.toBuilder().put("consumerGroup", consumerGroup).build();
    Context buildContext = newBaggage.storeInContext(parentContext);
    Context newContext = instrumenter.start(buildContext, context.getMsgList());

    // it's safe to store the scope in the rocketMq trace context, both before() and after() methods
    // are always called from the same thread; see:
    // - ConsumeMessageConcurrentlyService$ConsumeRequest#run()
    // - ConsumeMessageOrderlyService$ConsumeRequest#run()
    if (newContext != parentContext) {
      context.setMqTraceContext(ContextAndScope.create(newContext, newContext.makeCurrent()));
    }
  }

  @Override
  public void consumeMessageAfter(ConsumeMessageContext context) {
    if (context == null || context.getMsgList() == null || context.getMsgList().isEmpty()) {
      return;
    }
    if (context.getMqTraceContext() instanceof ContextAndScope) {
      ContextAndScope contextAndScope = (ContextAndScope) context.getMqTraceContext();
      contextAndScope.close();
      instrumenter.end(contextAndScope.getContext(), context.getMsgList());
    }
  }
}
