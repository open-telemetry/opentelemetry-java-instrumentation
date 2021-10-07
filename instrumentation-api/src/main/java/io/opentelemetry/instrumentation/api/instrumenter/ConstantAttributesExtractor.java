/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import org.checkerframework.checker.nullness.qual.Nullable;

final class ConstantAttributesExtractor<REQUEST, RESPONSE, T>
    extends AttributesExtractor<REQUEST, RESPONSE> {

  private final AttributeKey<T> attributeKey;
  private final T attributeValue;

  ConstantAttributesExtractor(AttributeKey<T> attributeKey, T attributeValue) {
    this.attributeKey = attributeKey;
    this.attributeValue = attributeValue;
  }

  @Override
  protected void onStart(AttributesBuilder attributes, REQUEST request) {
    attributes.put(attributeKey, attributeValue);
  }

  @Override
  protected void onEnd(
      AttributesBuilder attributes,
      REQUEST request,
      @Nullable RESPONSE response,
      @Nullable Throwable error) {}
}
