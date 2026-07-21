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
   * @see MessagingOperationType used to extract {@code <operation name>}.
   */
  public static <REQUEST> SpanNameExtractor<REQUEST> create(
      MessagingAttributesGetter<REQUEST, ?> getter, MessagingOperationType operationType) {
    return builder(getter, operationType).build();
  }

  /** Returns a messaging span name extractor for the given operation. */
  public static <REQUEST> SpanNameExtractor<REQUEST> create(
      MessagingAttributesGetter<REQUEST, ?> getter, MessageOperation operation) {
    return builder(getter, operation).build();
  }

  /**
   * Returns a new {@link MessagingSpanNameExtractorBuilder} that can be used to configure the
   * messaging span name extractor.
   */
  public static <REQUEST> MessagingSpanNameExtractorBuilder<REQUEST> builder(
      MessagingAttributesGetter<REQUEST, ?> getter, MessagingOperationType operationType) {
    return new MessagingSpanNameExtractorBuilder<>(getter, operationType, true);
  }

  /** Returns a messaging span name extractor builder for the given operation. */
  public static <REQUEST> MessagingSpanNameExtractorBuilder<REQUEST> builder(
      MessagingAttributesGetter<REQUEST, ?> getter, MessageOperation operation) {
    return new MessagingSpanNameExtractorBuilder<>(getter, operation.type(), false);
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

    return destinationName + " " + operationType.defaultOperationName();
  }
}
