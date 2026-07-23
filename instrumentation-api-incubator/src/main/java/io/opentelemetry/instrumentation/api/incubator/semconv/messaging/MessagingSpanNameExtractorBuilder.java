/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.messaging;

import static java.util.Objects.requireNonNull;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;

/** A builder of {@link MessagingSpanNameExtractor}. */
public final class MessagingSpanNameExtractorBuilder<REQUEST> {

  private final MessagingAttributesGetter<REQUEST, ?> getter;
  private final MessagingOperationType operationType;
  private final boolean supportsStableSemconv;
  private String operationName;

  MessagingSpanNameExtractorBuilder(
      MessagingAttributesGetter<REQUEST, ?> getter,
      MessagingOperationType operationType,
      boolean supportsStableSemconv) {
    this.getter = getter;
    this.operationType = requireNonNull(operationType, "operationType");
    this.supportsStableSemconv = supportsStableSemconv;
    this.operationName = operationType.defaultOperationName();
  }

  /** Configures the system-specific operation name used in the v1.43 messaging span name. */
  @CanIgnoreReturnValue
  public MessagingSpanNameExtractorBuilder<REQUEST> setOperationName(String operationName) {
    this.operationName = requireNonNull(operationName, "operationName");
    return this;
  }

  /**
   * Returns a new {@link MessagingSpanNameExtractor} with the settings of this {@link
   * MessagingSpanNameExtractorBuilder}.
   */
  public SpanNameExtractor<REQUEST> build() {
    return new MessagingSpanNameExtractor<>(
        getter, operationType, operationName, supportsStableSemconv);
  }
}
