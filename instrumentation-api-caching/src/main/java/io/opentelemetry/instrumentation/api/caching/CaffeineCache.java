/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.caching;

import java.util.Set;
import java.util.concurrent.Executor;
import org.checkerframework.checker.index.qual.NonNegative;
import org.checkerframework.checker.nullness.qual.NonNull;

interface CaffeineCache<K, V> extends Cache<K, V> {

  interface Builder<K, V> {

    void weakKeys();

    void weakValues();

    void maximumSize(@NonNegative long maximumSize);

    void executor(@NonNull Executor executor);

    Cache<K, V> build();
  }

  // Visible for testing
  Set<K> keySet();

  // Visible for testing
  void cleanup();
}
