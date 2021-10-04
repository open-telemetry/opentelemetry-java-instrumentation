/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.db.DbAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetAttributesOnStartExtractor;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Extractor of {@link io.opentelemetry.api.common.Attributes} for a given request and response.
 * Will be called {@linkplain #onStart(AttributesBuilder, Object) on start} with just the {@link
 * REQUEST} and again {@linkplain #onEnd(AttributesBuilder, Object, Object, Throwable) on end} with
 * both {@link REQUEST} and {@link RESPONSE} to allow populating attributes at each stage of a
 * request's lifecycle. It is best to populate as much as possible in {@link
 * #onStart(AttributesBuilder, Object)} to have it available during sampling.
 *
 * @see DbAttributesExtractor
 * @see HttpClientAttributesExtractor
 * @see NetAttributesOnStartExtractor
 */
public abstract class AttributesExtractor<REQUEST, RESPONSE> {
  /**
   * Extracts attributes from the {@link REQUEST} into the {@link AttributesBuilder} at the
   * beginning of a request.
   */
  protected abstract void onStart(AttributesBuilder attributes, REQUEST request);

  /**
   * Extracts attributes from the {@link REQUEST} and either {@link RESPONSE} or {@code error} into
   * the {@link AttributesBuilder} at the end of a request.
   */
  protected abstract void onEnd(
      AttributesBuilder attributes,
      REQUEST request,
      @Nullable RESPONSE response,
      @Nullable Throwable error);

  /**
   * Sets the {@code value} with the given {@code key} to the {@link AttributesBuilder} if {@code
   * value} is not {@code null}.
   */
  protected static <T> void set(
      AttributesBuilder attributes, AttributeKey<T> key, @Nullable T value) {
    if (value != null) {
      attributes.put(key, value);
    }
  }
}
