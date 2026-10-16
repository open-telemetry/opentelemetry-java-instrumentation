/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.internal;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class AgentCircularityLockTest {

  @Test
  void reportsOwnershipOnlyForCurrentThread() throws Exception {
    AgentCircularityLock lock = AgentCircularityLock.INSTANCE;
    assertThat(lock.isHeldByCurrentThread()).isFalse();
    assertThat(lock.acquire()).isTrue();
    try {
      assertThat(lock.isHeldByCurrentThread()).isTrue();
      assertThat(lock.acquire()).isFalse();
      CompletableFuture.runAsync(
              () -> {
                assertThat(lock.isHeldByCurrentThread()).isFalse();
                assertThat(lock.acquire()).isTrue();
                try {
                  assertThat(lock.isHeldByCurrentThread()).isTrue();
                } finally {
                  lock.release();
                }
                assertThat(lock.isHeldByCurrentThread()).isFalse();
              })
          .get(10, SECONDS);
      assertThat(lock.isHeldByCurrentThread()).isTrue();
    } finally {
      lock.release();
    }
    assertThat(lock.isHeldByCurrentThread()).isFalse();
  }
}
