/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.cache;

class PatchCaffeineTest {

//  @Test
//  void cleanupNotForkJoinTask() {
//    AtomicReference<AssertionError> errorRef = new AtomicReference<>();
//    io.opentelemetry.instrumentation.api.caching.Cache<String, String> cache =
//        io.opentelemetry.instrumentation.api.caching.Cache.builder()
//            .setExecutor(
//                task -> {
//                  try {
//                    assertThat(task).isNotInstanceOf(ForkJoinTask.class);
//                  } catch (AssertionError e) {
//                    errorRef.set(e);
//                  }
//                })
//            .setMaximumSize(1)
//            .build();
//    assertThat(cache.computeIfAbsent("cat", unused -> "meow")).isEqualTo("meow");
//    assertThat(cache.computeIfAbsent("dog", unused -> "bark")).isEqualTo("bark");
//    AssertionError error = errorRef.get();
//    if (error != null) {
//      throw error;
//    }
//  }
}
