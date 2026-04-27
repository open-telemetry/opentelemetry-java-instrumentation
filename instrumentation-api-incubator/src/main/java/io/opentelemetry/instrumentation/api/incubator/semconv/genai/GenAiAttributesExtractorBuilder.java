/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.genai;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;

/** A builder of {@link GenAiAttributesExtractor}. */
public final class GenAiAttributesExtractorBuilder<REQUEST, RESPONSE> {

  final GenAiAttributesGetter<REQUEST, RESPONSE> getter;
  boolean captureMessageContent = false;

  GenAiAttributesExtractorBuilder(GenAiAttributesGetter<REQUEST, RESPONSE> getter) {
    this.getter = getter;
  }

  /**
   * Sets whether the message content should be captured. Disabled by default.
   *
   * <p>WARNING: captured message content may contain sensitive information such as personally
   * identifiable information or protected health info.
   */
  @CanIgnoreReturnValue
  public GenAiAttributesExtractorBuilder<REQUEST, RESPONSE> setCaptureMessageContent(
      boolean captureMessageContent) {
    this.captureMessageContent = captureMessageContent;
    return this;
  }

  /**
   * Returns a new {@link GenAiAttributesExtractor} with the settings of this {@link
   * GenAiAttributesExtractorBuilder}.
   */
  public AttributesExtractor<REQUEST, RESPONSE> build() {
    return new GenAiAttributesExtractor<>(this);
  }
}
