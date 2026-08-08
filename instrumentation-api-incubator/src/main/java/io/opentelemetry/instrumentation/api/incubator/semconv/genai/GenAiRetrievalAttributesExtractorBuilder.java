/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.genai;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;

/** A builder of {@link GenAiRetrievalAttributesExtractor}. */
public final class GenAiRetrievalAttributesExtractorBuilder<REQUEST, RESPONSE> {

  final GenAiRetrievalAttributesGetter<REQUEST, RESPONSE> attributesGetter;
  boolean captureMessageContent;

  GenAiRetrievalAttributesExtractorBuilder(
      GenAiRetrievalAttributesGetter<REQUEST, RESPONSE> attributesGetter) {
    this.attributesGetter = attributesGetter;
  }

  /**
   * Sets whether to capture the retrieval query text ({@code gen_ai.retrieval.query.text}). Since
   * query text may contain sensitive information, it is not captured by default.
   */
  @CanIgnoreReturnValue
  public GenAiRetrievalAttributesExtractorBuilder<REQUEST, RESPONSE> setCaptureMessageContent(
      boolean captureMessageContent) {
    this.captureMessageContent = captureMessageContent;
    return this;
  }

  /**
   * Returns a new {@link GenAiRetrievalAttributesExtractor} with the settings of this {@link
   * GenAiRetrievalAttributesExtractorBuilder}.
   */
  public AttributesExtractor<REQUEST, RESPONSE> build() {
    return new GenAiRetrievalAttributesExtractor<>(attributesGetter, captureMessageContent);
  }
}
