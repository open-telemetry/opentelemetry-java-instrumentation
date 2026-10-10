/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ScopedThreadSuppressionTest {

  @Test
  void acquisitionOwnership() {
    ScopedThreadSuppression suppression = new ScopedThreadSuppression();

    assertThat(suppression.isActive()).isFalse();

    boolean acquired = suppression.tryAcquire();
    assertThat(acquired).isTrue();
    assertThat(suppression.isActive()).isTrue();

    boolean nestedAcquired = suppression.tryAcquire();
    assertThat(nestedAcquired).isFalse();
    if (nestedAcquired) {
      suppression.release();
    }
    assertThat(suppression.isActive()).isTrue();

    if (acquired) {
      suppression.release();
    }
    assertThat(suppression.isActive()).isFalse();

    assertThat(suppression.tryAcquire()).isTrue();
    assertThat(suppression.isActive()).isTrue();
    suppression.release();
    assertThat(suppression.isActive()).isFalse();
  }

  @Test
  void threadIsolation() throws InterruptedException {
    ScopedThreadSuppression suppression = new ScopedThreadSuppression();
    AtomicReference<Throwable> failure = new AtomicReference<>();

    assertThat(suppression.tryAcquire()).isTrue();

    Thread thread =
        new Thread(
            () -> {
              try {
                assertThat(suppression.isActive()).isFalse();
                assertThat(suppression.tryAcquire()).isTrue();
                assertThat(suppression.isActive()).isTrue();
                suppression.release();
                assertThat(suppression.isActive()).isFalse();
              } catch (Throwable t) {
                failure.set(t);
              }
            });
    thread.start();
    thread.join();

    assertThat(failure.get()).isNull();
    assertThat(suppression.isActive()).isTrue();
    suppression.release();
  }
}
