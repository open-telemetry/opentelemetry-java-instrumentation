/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.messaging;

import static java.util.Collections.emptyList;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import java.util.ArrayList;
import java.util.Collection;

/** A builder of {@link MessagingAttributesExtractor}. */
public final class MessagingAttributesExtractorBuilder<REQUEST, RESPONSE> {

  final MessagingAttributesGetter<REQUEST, RESPONSE> getter;
  private final MessagingOperationType operationType;
  private final String operationName;
  Collection<String> capturedHeaders = emptyList();

  MessagingAttributesExtractorBuilder(
      MessagingAttributesGetter<REQUEST, RESPONSE> getter,
      MessagingOperationType operationType,
      String operationName) {
    this.getter = getter;
    this.operationType = operationType;
    this.operationName = operationName;
  }

  /**
   * Configures which message headers are captured as span attributes.
   *
   * <p>Header values are captured under the {@code messaging.header.<name>} attribute key, where
   * {@code <name>} is the header name with its original spelling, including dashes.
   *
   * <p>Names are looked up literally using the underlying messaging library's header-name case
   * sensitivity. {@code *} and {@code ?} are literal characters, not wildcards. By default, no
   * headers are captured.
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
    return new MessagingAttributesExtractor<>(
        getter, operationType, operationName, capturedHeaders);
  }
}
