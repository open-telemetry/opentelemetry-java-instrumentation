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
   * @see MessagingAttributesExtractor#destination(Object) used to extract {@code <destination
   *     name>}.
   * @see MessagingAttributesExtractor#operation() used to extract {@code <operation name>}.
   */
  public static <REQUEST> SpanNameExtractor<REQUEST> create(
      MessagingAttributesExtractor<REQUEST, ?> attributesExtractor) {
    return new MessagingSpanNameExtractor<>(attributesExtractor);
  }

  private final MessagingAttributesExtractor<REQUEST, ?> attributesExtractor;

  private MessagingSpanNameExtractor(MessagingAttributesExtractor<REQUEST, ?> attributesExtractor) {
    this.attributesExtractor = attributesExtractor;
  }

  @Override
  public String extract(REQUEST request) {
    String destinationName =
        attributesExtractor.temporaryDestination(request)
            ? MessagingAttributesExtractor.TEMP_DESTINATION_NAME
            : attributesExtractor.destination(request);
    if (destinationName == null) {
      destinationName = "unknown";
    }

    MessageOperation operation = attributesExtractor.operation();
    return destinationName + " " + operation.operationName();
  }
}
