/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.vertx.redis.client;

import java.util.function.Supplier;

final class ConstantSupplier<T> implements Supplier<T> {

  private final T value;

  ConstantSupplier(T value) {
    this.value = value;
  }

  @Override
  public T get() {
    return value;
  }
}
