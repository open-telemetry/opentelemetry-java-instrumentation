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

  TracingSendMessageHookImpl(Instrumenter<SendMessageContext, SendMessageContext> instrumenter) {
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
    Context otelContext = instrumenter.start(parentContext, context);
//    Context otelContext =
//        tracer.startProducerSpan(Context.current(), context.getBrokerAddr(), context.getMessage());
//    if (propagationEnabled) {
//      tracer.inject(otelContext, context.getMessage().getProperties(), MapSetter.INSTANCE);
//    }
    context.setMqTraceContext(otelContext);
  }

  @Override
  public void sendMessageAfter(SendMessageContext context) {
    if (context == null) {
      return;
    }
    if (context.getMqTraceContext() instanceof Context) {
      Context otelContext = (Context) context.getMqTraceContext();
      instrumenter.end(otelContext, context, context, null);
    }
  }
}
