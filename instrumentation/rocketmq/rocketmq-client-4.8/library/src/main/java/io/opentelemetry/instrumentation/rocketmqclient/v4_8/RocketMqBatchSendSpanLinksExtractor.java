/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmqclient.v4_8;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.SpanLinksBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanLinksExtractor;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import java.util.List;
import org.apache.rocketmq.client.hook.SendMessageContext;

final class RocketMqBatchSendSpanLinksExtractor implements SpanLinksExtractor<SendMessageContext> {
  private static final VirtualField<SendMessageContext, List<Context>> MESSAGE_CREATION_CONTEXTS =
      VirtualField.find(SendMessageContext.class, List.class, "messageCreationContexts");

  static void setContexts(SendMessageContext request, List<Context> contexts) {
    MESSAGE_CREATION_CONTEXTS.set(request, contexts);
  }

  static void clearContexts(SendMessageContext request) {
    MESSAGE_CREATION_CONTEXTS.set(request, null);
  }

  static boolean isBatchRequest(SendMessageContext request) {
    return MESSAGE_CREATION_CONTEXTS.get(request) != null;
  }

  static long getBatchMessageCount(SendMessageContext request) {
    List<Context> contexts = MESSAGE_CREATION_CONTEXTS.get(request);
    return contexts == null ? 0 : contexts.size();
  }

  static boolean allMessagesHaveCreationContext(SendMessageContext request) {
    List<Context> contexts = MESSAGE_CREATION_CONTEXTS.get(request);
    if (contexts == null || contexts.isEmpty()) {
      return false;
    }
    for (Context context : contexts) {
      if (!Span.fromContext(context).getSpanContext().isValid()) {
        return false;
      }
    }
    return true;
  }

  @Override
  public void extract(
      SpanLinksBuilder spanLinks, Context parentContext, SendMessageContext request) {
    List<Context> contexts = MESSAGE_CREATION_CONTEXTS.get(request);
    if (contexts == null) {
      return;
    }
    for (Context context : contexts) {
      SpanContext spanContext = Span.fromContext(context).getSpanContext();
      spanLinks.addLink(spanContext);
    }
  }
}
