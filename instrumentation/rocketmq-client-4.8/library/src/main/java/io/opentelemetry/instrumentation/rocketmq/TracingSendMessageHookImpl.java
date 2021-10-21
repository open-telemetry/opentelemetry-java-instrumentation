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

  private final Instrumenter<SendMessageContext, SendMessageContext> instrumenter;
//  private final boolean propagationEnabled;

  TracingSendMessageHookImpl(Instrumenter<SendMessageContext, SendMessageContext> instrumenter,
      boolean propagationEnabled) {
    this.instrumenter = instrumenter;
//    this.propagationEnabled = propagationEnabled;
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
    Context otelContext = instrumenter.start(parentContext, context);
//    if (propagationEnabled) {
//      instrumenter.inject(otelContext, context.getMessage().getProperties(), SETTER);
//    }
    context.setMqTraceContext(otelContext);
  }

  @Override
  public void sendMessageAfter(SendMessageContext context) {
    if (context == null || context.getMqTraceContext() == null || context.getSendResult() == null) {
      return;
    }
    if (context.getMqTraceContext() instanceof Context) {
      Context otelContext = (Context) context.getMqTraceContext();
      instrumenter.end(otelContext, context, context, null);
    }
  }
}
