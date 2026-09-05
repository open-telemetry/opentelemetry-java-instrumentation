/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0.rx;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import io.lettuce.core.protocol.Command;
import io.lettuce.core.protocol.CommandType;
import io.lettuce.core.protocol.RedisCommand;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class LettuceReactiveCommandSupplierTest {

  @Test
  void concurrentGetDoesNotHoldLockWhileCallingDelegate() throws Exception {
    CountDownLatch delegateEntered = new CountDownLatch(1);
    CountDownLatch releaseDelegate = new CountDownLatch(1);
    AtomicInteger delegateCalls = new AtomicInteger();
    Supplier<RedisCommand<String, String, String>> delegate =
        () -> {
          if (delegateCalls.incrementAndGet() == 2) {
            delegateEntered.countDown();
            try {
              releaseDelegate.await();
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              throw new AssertionError(e);
            }
          }
          return command();
        };
    LettuceReactiveCommandSupplier<String, String, String> supplier =
        new LettuceReactiveCommandSupplier<>(delegate);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      assertThat(supplier.get()).isSameAs(supplier.getTracingCommand());
      Future<RedisCommand<String, String, String>> blocked = executor.submit(supplier::get);
      assertThat(delegateEntered.await(10, SECONDS)).isTrue();

      Future<RedisCommand<String, String, String>> concurrent = executor.submit(supplier::get);
      assertThat(concurrent.get(10, SECONDS)).isNotNull();

      releaseDelegate.countDown();
      assertThat(blocked.get(10, SECONDS)).isNotNull();
    } finally {
      releaseDelegate.countDown();
      executor.shutdownNow();
    }
  }

  @Test
  void delegateCanReenterSupplier() {
    AtomicReference<LettuceReactiveCommandSupplier<String, String, String>> reference =
        new AtomicReference<>();
    AtomicInteger delegateCalls = new AtomicInteger();
    Supplier<RedisCommand<String, String, String>> delegate =
        () -> {
          if (delegateCalls.incrementAndGet() == 2) {
            reference.get().get();
          }
          return command();
        };
    LettuceReactiveCommandSupplier<String, String, String> supplier =
        new LettuceReactiveCommandSupplier<>(delegate);
    reference.set(supplier);

    supplier.get();
    assertThat(supplier.get()).isNotNull();
  }

  private static RedisCommand<String, String, String> command() {
    return new Command<>(CommandType.GET, null);
  }
}
