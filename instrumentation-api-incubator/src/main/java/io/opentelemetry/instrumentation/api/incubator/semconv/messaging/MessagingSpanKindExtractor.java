/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.messaging;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;

/** Selects messaging span kinds according to the configured semantic convention version. */
public final class MessagingSpanKindExtractor {

  /**
   * Returns a span kind extractor following the <a
   * href="https://github.com/open-telemetry/semantic-conventions/blob/v1.43.0/docs/messaging/messaging-spans.md#span-kind">v1.43
   * messaging span kind conventions</a>.
   */
  public static <REQUEST> SpanKindExtractor<REQUEST> create(MessagingOperationType operationType) {
    return create(operationType, true);
  }

  /**
   * Returns a span kind extractor following the <a
   * href="https://github.com/open-telemetry/semantic-conventions/blob/v1.43.0/docs/messaging/messaging-spans.md#span-kind">v1.43
   * messaging span kind conventions</a>.
   *
   * @param isSpanContextPropagated whether the context of a {@link MessagingOperationType#SEND}
   *     span is propagated as the message creation context; ignored for other operation types
   */
  public static <REQUEST> SpanKindExtractor<REQUEST> create(
      MessagingOperationType operationType, boolean isSpanContextPropagated) {
    SpanKind spanKind;
    switch (operationType) {
      case CREATE:
        spanKind = SpanKind.PRODUCER;
        break;
      case SEND:
        spanKind =
            emitStableMessagingSemconv() && !isSpanContextPropagated
                ? SpanKind.CLIENT
                : SpanKind.PRODUCER;
        break;
      case RECEIVE:
        spanKind = emitStableMessagingSemconv() ? SpanKind.CLIENT : SpanKind.CONSUMER;
        break;
      case PROCESS:
        spanKind = SpanKind.CONSUMER;
        break;
      case SETTLE:
        spanKind = SpanKind.CLIENT;
        break;
      default:
        throw new IllegalStateException("Can't possibly happen");
    }
    SpanKind result = spanKind;
    return request -> result;
  }

  /** Returns a span kind extractor for the given operation. */
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
