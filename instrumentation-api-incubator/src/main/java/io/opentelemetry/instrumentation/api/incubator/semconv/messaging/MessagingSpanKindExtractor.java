/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.messaging;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import java.util.function.Predicate;

/** Selects messaging span kinds according to the configured semantic convention version. */
public final class MessagingSpanKindExtractor {

  /**
   * Returns a span kind extractor following the <a
   * href="https://github.com/open-telemetry/semantic-conventions/blob/v1.43.0/docs/messaging/messaging-spans.md#span-kind">v1.43
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
   * href="https://github.com/open-telemetry/semantic-conventions/blob/v1.43.0/docs/messaging/messaging-spans.md#span-kind">v1.43
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
            request ->
                emitStableMessagingSemconv() && !spanContextPropagated.test(request)
                    ? SpanKind.CLIENT
                    : SpanKind.PRODUCER;
        break;
      case RECEIVE:
        SpanKind receiveKind = emitStableMessagingSemconv() ? SpanKind.CLIENT : SpanKind.CONSUMER;
        spanKindExtractor = request -> receiveKind;
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

  /**
   * @deprecated Use {@link #create(MessagingOperationType)}. May be removed in the next minor
   *     release.
   */
  @Deprecated // may be removed in the next minor release
  public static <REQUEST> SpanKindExtractor<REQUEST> create(MessageOperation operation) {
    SpanKind spanKind;
    switch (operation) {
      case PUBLISH:
        spanKind = SpanKind.PRODUCER;
        break;
      case RECEIVE:
      case PROCESS:
        spanKind = SpanKind.CONSUMER;
        break;
      default:
        throw new IllegalStateException("Can't possibly happen");
    }
    return request -> spanKind;
  }

  private MessagingSpanKindExtractor() {}
}
