/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.messaging;

import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;

public final class MessagingSpanNameExtractor<REQUEST> implements SpanNameExtractor<REQUEST> {

  /**
   * Returns a {@link SpanNameExtractor} that constructs the span name according to <a
   * href="https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/trace/semantic_conventions/messaging.md#span-name">
   * messaging semantic conventions</a>: {@code <destination name> <operation name>}.
   *
   * @see MessagingAttributesGetter#destination(Object) used to extract {@code <destination name>}.
   * @see MessageOperation used to extract {@code <operation name>}.
   */
  public static <REQUEST> SpanNameExtractor<REQUEST> create(
      MessagingAttributesGetter<REQUEST, ?> getter, MessageOperation operation) {
    return new MessagingSpanNameExtractor<>(getter, operation);
  }

  private final MessagingAttributesGetter<REQUEST, ?> getter;
  private final MessageOperation operation;

  private MessagingSpanNameExtractor(
      MessagingAttributesGetter<REQUEST, ?> getter, MessageOperation operation) {
    this.getter = getter;
    this.operation = operation;
  }

  @SuppressWarnings("deprecation") // operationName
  @Override
  public String extract(REQUEST request) {
    String destinationName =
        getter.temporaryDestination(request)
            ? MessagingAttributesExtractor.TEMP_DESTINATION_NAME
            : getter.destination(request);
    if (destinationName == null) {
      destinationName = "unknown";
    }

    return destinationName + " " + operation.operationName();
  }
}
