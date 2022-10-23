/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmqclient.v4_8;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import org.apache.rocketmq.client.hook.SendMessageContext;
import org.apache.rocketmq.client.hook.SendMessageHook;

final class TracingSendMessageHookImpl implements SendMessageHook {

  private static final VirtualField<SendMessageContext, Context> contextField =
      VirtualField.find(SendMessageContext.class, Context.class);

  private final Instrumenter<SendMessageContext, Void> instrumenter;

  TracingSendMessageHookImpl(Instrumenter<SendMessageContext, Void> instrumenter) {
    this.instrumenter = instrumenter;
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
    Context parentContext = Context.current();
    if (!instrumenter.shouldStart(parentContext, context)) {
      return;
    }
    contextField.set(context, instrumenter.start(parentContext, context));
  }

  @Override
  public void sendMessageAfter(SendMessageContext context) {
    if (context == null) {
      return;
    }
    Context otelContext = contextField.get(context);
    if (otelContext != null
        && (context.getSendResult() != null || context.getException() != null)) {
      instrumenter.end(otelContext, context, null, context.getException());
    }
  }
}
