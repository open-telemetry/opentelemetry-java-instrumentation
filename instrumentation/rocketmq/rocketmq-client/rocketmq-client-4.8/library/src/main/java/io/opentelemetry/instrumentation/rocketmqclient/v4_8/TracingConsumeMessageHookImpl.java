/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmqclient.v4_8;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import org.apache.rocketmq.client.hook.ConsumeMessageContext;
import org.apache.rocketmq.client.hook.ConsumeMessageHook;

final class TracingConsumeMessageHookImpl implements ConsumeMessageHook {

  private static final VirtualField<ConsumeMessageContext, ContextAndScope> contextAndScopeField =
      VirtualField.find(ConsumeMessageContext.class, ContextAndScope.class);

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
    Context newContext = instrumenter.start(parentContext, context.getMsgList());

    if (newContext != parentContext) {
      contextAndScopeField.set(
          context, ContextAndScope.create(newContext, newContext.makeCurrent()));
    }
  }

  @Override
  public void consumeMessageAfter(ConsumeMessageContext context) {
    if (context == null || context.getMsgList() == null || context.getMsgList().isEmpty()) {
      return;
    }
    ContextAndScope contextAndScope = contextAndScopeField.get(context);
    if (contextAndScope != null) {
      contextAndScope.close();
      instrumenter.end(contextAndScope.getContext(), context.getMsgList());
    }
  }
}
