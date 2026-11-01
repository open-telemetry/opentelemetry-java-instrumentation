/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.messaging;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import java.util.function.Predicate;

/** Selects messaging span kinds. */
public final class MessagingSpanKindExtractor {

  /**
   * Returns a span kind extractor following the <a
   * href="https://github.com/open-telemetry/semantic-conventions/blob/v1.43.0/docs/messaging/messaging-spans.md#span-kind">
   * messaging span kind conventions</a>.
   *
   * <p>{@link MessagingOperationType#SEND} spans are treated as propagating their span context as
   * the message creation context; use {@link #create(MessagingOperationType, Predicate)} when that
   * varies per request.
   */
  public static <REQUEST> SpanKindExtractor<REQUEST> create(MessagingOperationType operationType) {
    return create(operationType, request -> true);
  }

  /**
   * Returns a span kind extractor following the <a
   * href="https://github.com/open-telemetry/semantic-conventions/blob/v1.43.0/docs/messaging/messaging-spans.md#span-kind">
   * messaging span kind conventions</a>.
   *
   * @param spanContextPropagated tells whether the context of a {@link MessagingOperationType#SEND}
   *     span is propagated as the message creation context; ignored for other operation types
   */
  public static <REQUEST> SpanKindExtractor<REQUEST> create(
      MessagingOperationType operationType, Predicate<REQUEST> spanContextPropagated) {
    SpanKindExtractor<REQUEST> spanKindExtractor;
    switch (operationType) {
      case CREATE:
        spanKindExtractor = request -> SpanKind.PRODUCER;
        break;
      case SEND:
        spanKindExtractor =
            request -> spanContextPropagated.test(request) ? SpanKind.PRODUCER : SpanKind.CLIENT;
        break;
      case RECEIVE:
        spanKindExtractor = request -> SpanKind.CLIENT;
        break;
      case PROCESS:
        spanKindExtractor = request -> SpanKind.CONSUMER;
        break;
      case SETTLE:
        spanKindExtractor = request -> SpanKind.CLIENT;
        break;
      default:
        throw new IllegalStateException("Can't possibly happen");
    }
    return spanKindExtractor;
  }

  private MessagingSpanKindExtractor() {}
}
