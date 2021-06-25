/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal;

@FunctionalInterface
public interface ThrowingSupplier<T, E extends Throwable> {

  T call() throws E;
}
