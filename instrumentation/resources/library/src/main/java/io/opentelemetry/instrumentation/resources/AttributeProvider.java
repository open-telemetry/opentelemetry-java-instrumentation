/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.common.AttributeKey;
import java.util.Optional;
import java.util.function.Function;

public interface AttributeProvider<D> {
  Optional<D> readData();

  void registerAttributes(Builder<D> builder);

  public interface Builder<D> {
    @CanIgnoreReturnValue
    public <T> Builder<D> add(AttributeKey<T> key, Function<D, Optional<T>> getter);
  }
}
