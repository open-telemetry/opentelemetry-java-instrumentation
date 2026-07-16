/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.messaging;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;

import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;

public final class MessagingSpanNameExtractor<REQUEST> implements SpanNameExtractor<REQUEST> {

  /**
   * Returns a {@link SpanNameExtractor} that constructs the span name according to <a
   * href="https://github.com/open-telemetry/semantic-conventions/blob/v1.43.0/docs/messaging/messaging-spans.md#span-name">
   * messaging semantic conventions</a>.
   *
   * @see MessagingAttributesGetter#getDestination(Object) used to extract {@code <destination
   *     name>}.
   * @see MessageOperation used to extract {@code <operation name>}.
   */
  public static <REQUEST> SpanNameExtractor<REQUEST> create(
      MessagingAttributesGetter<REQUEST, ?> getter, MessageOperation operation) {
    return new MessagingSpanNameExtractor<>(getter, MessagingOperation.create(operation));
  }

  /** Returns a {@link SpanNameExtractor} with a system-specific v1.43 operation name. */
  public static <REQUEST> SpanNameExtractor<REQUEST> create(
      MessagingAttributesGetter<REQUEST, ?> getter,
      MessageOperation operation,
      String operationName) {
    return new MessagingSpanNameExtractor<>(
        getter, MessagingOperation.create(operation, operationName));
  }

  private final MessagingAttributesGetter<REQUEST, ?> getter;
  private final MessagingOperation operation;

  private MessagingSpanNameExtractor(
      MessagingAttributesGetter<REQUEST, ?> getter, MessagingOperation operation) {
    this.getter = getter;
    this.operation = operation;
  }

  @Override
  public String extract(REQUEST request) {
    if (emitStableMessagingSemconv()) {
      String destinationName = getter.getDestinationTemplate(request);
      if (destinationName == null
          && !getter.isTemporaryDestination(request)
          && !getter.isAnonymousDestination(request)) {
        destinationName = getter.getDestination(request);
      }
      return destinationName == null ? operation.name() : operation.name() + " " + destinationName;
    }

    String destinationName =
        getter.isTemporaryDestination(request)
            ? MessagingAttributesExtractor.TEMP_DESTINATION_NAME
            : getter.getDestination(request);
    if (destinationName == null) {
      destinationName = "unknown";
    }

    return destinationName + " " + operation.operation().operationName();
  }
}
