/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class WeakRefRunnableTest {

  @Test
  void delegatesWhileReferentIsAlive() {
    AtomicInteger count = new AtomicInteger();
    Runnable delegate = count::incrementAndGet;
    WeakRefRunnable weak = new WeakRefRunnable(new WeakReference<>(delegate));

    weak.run();
    weak.run();
    assertThat(count.get()).isEqualTo(2);

    // prevent delegate from being collected during the test
    assertThat(delegate).isNotNull();
  }

  @Test
  void closesAutoCloseableExactlyOnceOnExpiry() throws Exception {
    AtomicInteger closeCount = new AtomicInteger();
    AutoCloseable closeable =
        () -> {
          closeCount.incrementAndGet();
        };

    WeakRefRunnable weak = new WeakRefRunnable(new WeakReference<>(null));
    weak.closeWhenCollected(closeable);

    // First run detects cleared ref and closes the instrument
    weak.run();
    assertThat(closeCount.get()).isEqualTo(1);

    // Subsequent runs should NOT close again
    weak.run();
    weak.run();
    assertThat(closeCount.get()).isEqualTo(1);
  }

  @Test
  void doesNotCloseWhileReferentAlive() {
    AtomicBoolean closed = new AtomicBoolean();
    Runnable delegate = () -> {};
    WeakRefRunnable weak = new WeakRefRunnable(new WeakReference<>(delegate));
    weak.closeWhenCollected(() -> closed.set(true));

    weak.run();
    assertThat(closed.get()).isFalse();

    // prevent delegate from being collected during the test
    assertThat(delegate).isNotNull();
  }

  @Test
  void closeExceptionIsSuppressed() {
    WeakRefRunnable weak = new WeakRefRunnable(new WeakReference<>(null));
    weak.closeWhenCollected(
        () -> {
          throw new Exception("boom");
        });

    // Should not throw
    weak.run();
  }
}
