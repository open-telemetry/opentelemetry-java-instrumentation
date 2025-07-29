/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.common.AttributeKey;
import java.util.Optional;
import java.util.function.Function;

/**
 * An easier alternative to {@link io.opentelemetry.sdk.autoconfigure.spi.ResourceProvider}, which
 * avoids some common pitfalls and boilerplate.
 *
 * <p>An example of how to use this interface can be found in {@link ManifestResourceProvider}.
 */
interface AttributeProvider<D> {
  Optional<D> readData();

  void registerAttributes(Builder<D> builder);

  interface Builder<D> {
    @CanIgnoreReturnValue
    <T> Builder<D> add(AttributeKey<T> key, Function<D, Optional<T>> getter);
  }
}
