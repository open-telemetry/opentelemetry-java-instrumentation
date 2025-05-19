/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import static java.util.Objects.requireNonNull;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import javax.annotation.Nullable;

/**
 * Extractor of {@link Attributes} for a given request and response. Will be called {@linkplain
 * #onStart(AttributesBuilder, Context, Object) on start} with just the {@link REQUEST} and again
 * {@linkplain #onEnd(AttributesBuilder, Context, Object, Object, Throwable) on end} with both
 * {@link REQUEST} and {@link RESPONSE} to allow populating attributes at each stage of a request's
 * lifecycle. It is best to populate as many attributes as possible in {@link
 * #onStart(AttributesBuilder, Context, Object)} to have them available during span sampling.
 */
public interface AttributesExtractor<REQUEST, RESPONSE> {

  /**
   * Extracts attributes from the {@link Context} and the {@link REQUEST} into the {@link
   * AttributesBuilder} at the beginning of a request.
   */
  void onStart(AttributesBuilder attributes, Context parentContext, REQUEST request);

  /**
   * Extracts attributes from the {@link Context}, the {@link REQUEST} and either {@link RESPONSE}
   * or {@code error} into the {@link AttributesBuilder} at the end of a request.
   */
  void onEnd(
      AttributesBuilder attributes,
      Context context,
      REQUEST request,
      @Nullable RESPONSE response,
      @Nullable Throwable error);

  /**
   * Returns an {@link AttributesExtractor} implementation that always extracts the provided
   * constant value on the request start.
   */
  static <REQUEST, RESPONSE, T> AttributesExtractor<REQUEST, RESPONSE> constant(
      AttributeKey<T> attributeKey, T attributeValue) {
    return new ConstantAttributesExtractor<>(
        requireNonNull(attributeKey, "attributeKey"),
        requireNonNull(attributeValue, "attributeValue"));
  }
}
