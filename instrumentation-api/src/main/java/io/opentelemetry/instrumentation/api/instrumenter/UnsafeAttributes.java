/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * The {@link AttributesBuilder} and {@link Attributes} used by the instrumentation API. We are able
 * to take advantage of the fact that we know our attributes builder cannot be reused to create
 * multiple Attributes instances. So we use just one storage for both the builder and attributes. A
 * couple of methods still require copying to satisfy the interface contracts, but in practice
 * should never be called by user code even though they can.
 */
final class UnsafeAttributes extends HashMap<AttributeKey<?>, Object>
    implements Attributes, AttributesBuilder {

  private static final long serialVersionUID = 1L;

  // Attributes

  @SuppressWarnings("unchecked")
  @Override
  public <T> T get(AttributeKey<T> key) {
    return (T) super.get(key);
  }

  @Override
  public Map<AttributeKey<?>, Object> asMap() {
    return this;
  }

  // This can be called by user code in a RequestListener so copy. In practice, it should not be
  // called as there is no real use case.
  @Override
  public AttributesBuilder toBuilder() {
    return Attributes.builder().putAll(this);
  }

  // AttributesBuilder

  // This can be called by user code in an AttributesExtractor so copy. In practice, it should not
  // be called as there is no real use case.
  @Override
  public Attributes build() {
    return toBuilder().build();
  }

  @Override
  public <T> AttributesBuilder put(AttributeKey<Long> key, int value) {
    return put(key, (long) value);
  }

  @Override
  public <T> AttributesBuilder put(AttributeKey<T> key, T value) {
    super.put(key, value);
    return this;
  }

  @Override
  public AttributesBuilder putAll(Attributes attributes) {
    attributes.forEach(this::put);
    return this;
  }

  @Override
  public void forEach(BiConsumer<? super AttributeKey<?>, ? super Object> action) {
    // https://github.com/open-telemetry/opentelemetry-java/issues/4161
    // Help out android desugaring by having an explicit call to HashMap.forEach, when forEach is
    // just called through Attributes.forEach desugaring is unable to correctly handle it.
    super.forEach(action);
  }
}
