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

  /**
   * @deprecated Use {@link #create(MessagingAttributesGetter, MessagingOperationType)}. Will be
   *     removed in 3.0.
   */
  @Deprecated // to be removed in 3.0
  public static <REQUEST> SpanNameExtractor<REQUEST> create(
      MessagingAttributesGetter<REQUEST, ?> getter, MessageOperation operation) {
    return create(getter, operation.type());
  }

  /**
   * Returns a new {@link MessagingSpanNameExtractorBuilder} that can be used to configure the
   * messaging span name extractor.
   */
  public static <REQUEST> MessagingSpanNameExtractorBuilder<REQUEST> builder(
      MessagingAttributesGetter<REQUEST, ?> getter, MessagingOperationType operationType) {
    return new MessagingSpanNameExtractorBuilder<>(getter, operationType);
  }

  /**
   * @deprecated Use {@link #builder(MessagingAttributesGetter, MessagingOperationType)}. Will be
   *     removed in 3.0.
   */
  @Deprecated // to be removed in 3.0
  public static <REQUEST> MessagingSpanNameExtractorBuilder<REQUEST> builder(
      MessagingAttributesGetter<REQUEST, ?> getter, MessageOperation operation) {
    return builder(getter, operation.type());
  }

  private final MessagingAttributesGetter<REQUEST, ?> getter;
  private final MessagingOperationType operationType;
  private final String operationName;

  MessagingSpanNameExtractor(
      MessagingAttributesGetter<REQUEST, ?> getter,
      MessagingOperationType operationType,
      String operationName) {
    this.getter = getter;
    this.operationType = operationType;
    this.operationName = operationName;
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
