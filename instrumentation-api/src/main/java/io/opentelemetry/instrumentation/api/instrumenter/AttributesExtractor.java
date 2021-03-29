/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;

/**
 * Extractor of {@link io.opentelemetry.api.common.Attributes} for a given request and response.
 * Will be called {@linkplain #onStart(AttributesBuilder, Object) on start} with just the {@link
 * REQUEST} and again {@linkplain #onEnd(AttributesBuilder, Object, Object) on end} with both {@link
 * REQUEST} and {@link RESPONSE} to allow populating attributes at each stage of a request's
 * lifecycle. It is best to populate as much as possible in {@link #onStart(AttributesBuilder,
 * Object)} to have it available during sampling.
 *
 * @see HttpAttributesExtractor
 * @see NetAttributesExtractor
 */
public abstract class AttributesExtractor<REQUEST, RESPONSE> {
  /**
   * Extracts attributes from the {@link REQUEST} into the {@link AttributesBuilder} at the
   * beginning of a request.
   */
  protected abstract void onStart(AttributesBuilder attributes, REQUEST request);

  /**
   * Extracts attributes from the {@link REQUEST} and {@link RESPONSE} into the {@link
   * AttributesBuilder} at the end of a request.
   */
  protected abstract void onEnd(AttributesBuilder attributes, REQUEST request, RESPONSE response);

  /**
   * Sets the {@code value} with the given {@code key} to the {@link AttributesBuilder} if {@code
   * value} is not {@code null}.
   */
  protected static <T> void set(AttributesBuilder attributes, AttributeKey<T> key, T value) {
    if (value != null) {
      attributes.put(key, value);
    }
  }
}
