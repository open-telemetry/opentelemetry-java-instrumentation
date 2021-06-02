/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.integration;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.SpanLinkExtractor;

final class MessageSpanLinkExtractor implements SpanLinkExtractor<MessageWithChannel> {
  private final SpanLinkExtractor<MessageWithChannel> delegate;

  MessageSpanLinkExtractor(SpanLinkExtractor<MessageWithChannel> delegate) {
    this.delegate = delegate;
  }

  @Override
  public SpanContext extract(Context parentContext, MessageWithChannel messageWithChannel) {
    SpanContext spanFromMessage = delegate.extract(parentContext, messageWithChannel);
    SpanContext parentSpan = Span.fromContext(parentContext).getSpanContext();
    if (referencesSameSpan(spanFromMessage, parentSpan)) {
      return SpanContext.getInvalid();
    }
    return spanFromMessage;
  }

  // SpanContext#equals() includes e.g. remote flag, which we don't really care about here
  // we just want to avoid adding links to spans with the same id, flags don't matter at all
  private static boolean referencesSameSpan(SpanContext spanFromMessage, SpanContext parentSpan) {
    return parentSpan.getTraceId().equals(spanFromMessage.getTraceId())
        && parentSpan.getSpanId().equals(spanFromMessage.getSpanId());
  }
}
