/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.messaging;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nullable;

/** A builder of {@link MessagingAttributesExtractor}. */
public final class MessagingAttributesExtractorBuilder<REQUEST, RESPONSE> {

  final MessagingAttributesGetter<REQUEST, RESPONSE> getter;
  @Nullable private final MessagingOperationType operationType;
  @Nullable private String operationName;
  private final boolean supportsStableSemconv;
  List<String> capturedHeaders = emptyList();

  MessagingAttributesExtractorBuilder(
      MessagingAttributesGetter<REQUEST, RESPONSE> getter,
      @Nullable MessagingOperationType operationType,
      boolean supportsStableSemconv) {
    this.getter = getter;
    this.operationType = operationType;
    this.operationName = operationType == null ? null : operationType.defaultOperationName();
    this.supportsStableSemconv = supportsStableSemconv;
  }

  /** Configures the system-specific operation name emitted as {@code messaging.operation.name}. */
  @CanIgnoreReturnValue
  public MessagingAttributesExtractorBuilder<REQUEST, RESPONSE> setOperationName(
      String operationName) {
    this.operationName = requireNonNull(operationName, "operationName");
    return this;
  }

  /**
   * Configures the messaging headers that will be captured as span attributes.
   *
   * <p>The messaging header values will be captured under the {@code messaging.header.<name>}
   * attribute key. The {@code <name>} part in the attribute key is the header name with dashes
   * replaced by underscores.
   *
   * @param capturedHeaders A list of messaging header names.
   */
  @CanIgnoreReturnValue
  public MessagingAttributesExtractorBuilder<REQUEST, RESPONSE> setCapturedHeaders(
      Collection<String> capturedHeaders) {
    this.capturedHeaders = new ArrayList<>(capturedHeaders);
    return this;
  }

  /**
   * Returns a new {@link MessagingAttributesExtractor} with the settings of this {@link
   * MessagingAttributesExtractorBuilder}.
   */
  public AttributesExtractor<REQUEST, RESPONSE> build() {
    if (supportsStableSemconv) {
      requireNonNull(operationName, "operationName");
    }
    return new MessagingAttributesExtractor<>(
        getter, operationType, operationName, supportsStableSemconv, capturedHeaders);
  }
}
