/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.messaging;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static java.util.Objects.requireNonNull;

import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;

public final class MessagingSpanNameExtractor<REQUEST> implements SpanNameExtractor<REQUEST> {

  /**
   * Returns a {@link SpanNameExtractor} that constructs the span name according to <a
   * href="https://github.com/open-telemetry/semantic-conventions/blob/v1.43.0/docs/messaging/messaging-spans.md#span-name">
   * messaging semantic conventions</a>.
   *
   * @param operationName the system-specific name of the operation, used as the {@code <operation
   *     name>} part of the span name, e.g. {@code send}, {@code poll} or {@code ack}.
   * @see MessagingAttributesGetter#getDestination(Object) used to extract {@code <destination
   *     name>}.
   */
  public static <REQUEST> SpanNameExtractor<REQUEST> create(
      MessagingAttributesGetter<REQUEST, ?> getter,
      MessagingOperationType operationType,
      String operationName) {
    return new MessagingSpanNameExtractor<>(
        getter,
        requireNonNull(operationType, "operationType"),
        requireNonNull(operationName, "operationName"),
        true);
  }

  /**
   * @deprecated Use {@link #create(MessagingAttributesGetter, MessagingOperationType, String)}. May
   *     be removed in the next minor release.
   */
  @Deprecated // may be removed in the next minor release
  public static <REQUEST> SpanNameExtractor<REQUEST> create(
      MessagingAttributesGetter<REQUEST, ?> getter, MessageOperation operation) {
    MessagingOperationType operationType = operation.type();
    return new MessagingSpanNameExtractor<>(
        getter, operationType, operationType.legacyOperationName(), false);
  }

  private final MessagingAttributesGetter<REQUEST, ?> getter;
  private final MessagingOperationType operationType;
  private final String operationName;
  private final boolean supportsStableSemconv;

  MessagingSpanNameExtractor(
      MessagingAttributesGetter<REQUEST, ?> getter,
      MessagingOperationType operationType,
      String operationName,
      boolean supportsStableSemconv) {
    this.getter = getter;
    this.operationType = operationType;
    this.operationName = operationName;
    this.supportsStableSemconv = supportsStableSemconv;
  }

  @Override
  public String extract(REQUEST request) {
    if (supportsStableSemconv && emitStableMessagingSemconv()) {
      String destinationName = getter.getDestinationTemplate(request);
      if (destinationName == null
          && !getter.isTemporaryDestination(request)
          && !getter.isAnonymousDestination(request)) {
        destinationName = getter.getDestination(request);
      }
      return destinationName == null ? operationName : operationName + " " + destinationName;
    }

    String destinationName =
        getter.isTemporaryDestination(request)
            ? MessagingAttributesExtractor.TEMP_DESTINATION_NAME
            : getter.getDestination(request);
    if (destinationName == null) {
      destinationName = "unknown";
    }

    return destinationName + " " + operationType.legacyOperationName();
  }
}
