/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.caching;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class PatchCaffeineTest {

  @Test
  void cleanupNotForkJoinTask() {
    AtomicReference<AssertionError> errorRef = new AtomicReference<>();
    Cache<String, String> cache =
        Cache.builder()
            .setExecutor(
                task -> {
                  try {
                    assertThat(task).isNotInstanceOf(ForkJoinTask.class);
                  } catch (AssertionError e) {
                    errorRef.set(e);
                  }
                })
            .setMaximumSize(1)
            .build();
    assertThat(cache.computeIfAbsent("cat", unused -> "meow")).isEqualTo("meow");
    assertThat(cache.computeIfAbsent("dog", unused -> "bark")).isEqualTo("bark");
    AssertionError error = errorRef.get();
    if (error != null) {
      throw error;
    }
  }
}
