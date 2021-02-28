/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmq;

import static io.opentelemetry.instrumentation.rocketmq.RocketMqProducerTracer.tracer;
import static io.opentelemetry.instrumentation.rocketmq.TextMapInjectAdapter.SETTER;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;
import org.apache.rocketmq.client.hook.SendMessageContext;
import org.apache.rocketmq.client.hook.SendMessageHook;

public class TracingSendMessageHookImpl implements SendMessageHook {

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
        tracer()
            .startProducerSpan(context.getBrokerAddr(), context.getMessage(), Context.current());
    if (RocketMqClientConfig.isPropagationEnabled()) {
      GlobalOpenTelemetry.getPropagators()
          .getTextMapPropagator()
          .inject(traceContext, context.getMessage().getProperties(), SETTER);
    }
    context.setMqTraceContext(traceContext);
  }

  @Override
  public void sendMessageAfter(SendMessageContext context) {
    if (context == null || context.getMqTraceContext() == null || context.getSendResult() == null) {
      return;
    }
    tracer().afterProduce((Context) context.getMqTraceContext(), context.getSendResult());
    tracer().end((Context) context.getMqTraceContext());
  }
}
