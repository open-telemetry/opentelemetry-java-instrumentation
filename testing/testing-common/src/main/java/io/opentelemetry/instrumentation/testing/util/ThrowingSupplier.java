/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.util;

import java.util.function.Supplier;

/**
 * A utility interface representing a {@link Supplier} that may throw.
 *
 * @param <E> Thrown exception type.
 */
@FunctionalInterface
public interface ThrowingSupplier<T, E extends Throwable> {
  T get() throws E;
}
