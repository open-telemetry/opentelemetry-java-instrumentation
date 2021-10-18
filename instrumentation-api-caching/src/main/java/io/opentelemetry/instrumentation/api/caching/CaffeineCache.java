/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.caching;

import java.util.Set;
import java.util.concurrent.Executor;
import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;

interface CaffeineCache<K, V> extends Cache<K, V> {

  interface Builder<K, V> {

    void weakKeys();

    void weakValues();

    void maximumSize(@Nonnegative long maximumSize);

    void executor(@Nonnull Executor executor);

    Cache<K, V> build();
  }

  // Visible for testing
  Set<K> keySet();

  // Visible for testing
  void cleanup();
}
