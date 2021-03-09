/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.util;

/**
 * A utility interface representing a {@link Runnable} that may throw.
 *
 * @param <E> Thrown exception type.
 */
@FunctionalInterface
public interface ThrowingRunnable<E extends Throwable> {
  void run() throws E;
}
