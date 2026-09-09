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
  private static final VirtualField<SendMessageContext, MessageCreationContexts>
      MESSAGE_CREATION_CONTEXTS =
          VirtualField.find(SendMessageContext.class, MessageCreationContexts.class);

  static void setContexts(SendMessageContext request, List<Context> contexts) {
    MESSAGE_CREATION_CONTEXTS.set(request, new MessageCreationContexts(contexts));
  }

  static void clearContexts(SendMessageContext request) {
    MESSAGE_CREATION_CONTEXTS.set(request, null);
  }

  static boolean hasCreationContexts(SendMessageContext request) {
    MessageCreationContexts contexts = MESSAGE_CREATION_CONTEXTS.get(request);
    if (contexts != null) {
      for (Context context : contexts.contexts) {
        if (Span.fromContext(context).getSpanContext().isValid()) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public void extract(
      SpanLinksBuilder spanLinks, Context parentContext, SendMessageContext request) {
    MessageCreationContexts contexts = MESSAGE_CREATION_CONTEXTS.get(request);
    if (contexts == null) {
      return;
    }
    for (Context context : contexts.contexts) {
      SpanContext spanContext = Span.fromContext(context).getSpanContext();
      if (spanContext.isValid()) {
        spanLinks.addLink(spanContext);
      }
    }
  }

  static final class MessageCreationContexts {
    private final List<Context> contexts;

    private MessageCreationContexts(List<Context> contexts) {
      this.contexts = contexts;
    }
  }
}
