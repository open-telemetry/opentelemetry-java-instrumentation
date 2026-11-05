/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.vertx.redis.client;

import java.util.function.Supplier;

public final class TestConstantSupplier {

  public static <T> Supplier<T> create(T value) {
    return new ConstantSupplier<>(value);
  }

  private TestConstantSupplier() {}
}
