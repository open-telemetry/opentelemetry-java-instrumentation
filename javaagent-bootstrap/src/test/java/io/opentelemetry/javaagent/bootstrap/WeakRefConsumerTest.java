/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class WeakRefConsumerTest {

  @Test
  void delegatesWhileReferentIsAlive() {
    AtomicInteger count = new AtomicInteger();
    Consumer<String> delegate = s -> count.incrementAndGet();
    WeakRefConsumer<String> weak = new WeakRefConsumer<>(new WeakReference<>(delegate));

    weak.accept("a");
    weak.accept("b");
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

    WeakRefConsumer<String> weak = new WeakRefConsumer<>(new WeakReference<>(null));
    weak.closeWhenCollected(closeable);

    // First accept detects cleared ref and closes the instrument
    weak.accept("x");
    assertThat(closeCount.get()).isEqualTo(1);

    // Subsequent accepts should NOT close again
    weak.accept("y");
    weak.accept("z");
    assertThat(closeCount.get()).isEqualTo(1);
  }

  @Test
  void doesNotCloseWhileReferentAlive() {
    AtomicBoolean closed = new AtomicBoolean();
    Consumer<String> delegate = s -> {};
    WeakRefConsumer<String> weak = new WeakRefConsumer<>(new WeakReference<>(delegate));
    weak.closeWhenCollected(() -> closed.set(true));

    weak.accept("x");
    assertThat(closed.get()).isFalse();

    // prevent delegate from being collected during the test
    assertThat(delegate).isNotNull();
  }

  @Test
  void closeExceptionIsSuppressed() {
    WeakRefConsumer<String> weak = new WeakRefConsumer<>(new WeakReference<>(null));
    weak.closeWhenCollected(
        () -> {
          throw new Exception("boom");
        });

    // Should not throw
    weak.accept("x");
  }
}
