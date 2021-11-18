/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.cache;

/** A builder of {@link Cache}. */
public final class CacheBuilder {
  /** Returns a new {@link Cache} with the settings of this {@link CacheBuilder}. */
  public <K, V> Cache<K, V> build() {
    return new WeakLockFreeCache<>();
  }

  CacheBuilder() {}
}
