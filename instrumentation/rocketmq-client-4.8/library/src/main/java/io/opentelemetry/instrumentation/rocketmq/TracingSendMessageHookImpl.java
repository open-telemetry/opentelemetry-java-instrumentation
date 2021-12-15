/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmq;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import org.apache.rocketmq.client.hook.SendMessageContext;
import org.apache.rocketmq.client.hook.SendMessageHook;

final class TracingSendMessageHookImpl implements SendMessageHook {

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
    context.setMqTraceContext(instrumenter.start(parentContext, context));
  }

  @Override
  public void sendMessageAfter(SendMessageContext context) {
    if (context == null) {
      return;
    }
    if (context.getMqTraceContext() instanceof Context) {
      Context otelContext = (Context) context.getMqTraceContext();
      instrumenter.end(otelContext, context, null, context.getException());
    }
  }
}
