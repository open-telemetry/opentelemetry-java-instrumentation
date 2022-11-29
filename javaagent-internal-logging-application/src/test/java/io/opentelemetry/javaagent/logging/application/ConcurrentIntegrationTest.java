/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.logging.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.javaagent.bootstrap.InternalLogger;
import io.opentelemetry.javaagent.bootstrap.InternalLogger.Level;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ConcurrentIntegrationTest {

  ScheduledExecutorService threadPool = Executors.newScheduledThreadPool(4);

  @AfterEach
  void tearDown() {
    threadPool.shutdownNow();
  }

  @Test
  void shouldLogEverything() throws InterruptedException {
    ApplicationLoggerFactory underTest = new ApplicationLoggerFactory(new InMemoryLogStore(-1));
    AtomicInteger count = new AtomicInteger(0);
    List<Integer> logMessages = new CopyOnWriteArrayList<>();
    List<ScheduledFuture<?>> futures = new ArrayList<>();

    for (int i = 0; i < 4; ++i) {
      futures.add(
          threadPool.scheduleAtFixedRate(
              () ->
                  underTest
                      .create("test")
                      .log(Level.INFO, String.valueOf(count.incrementAndGet()), null),
              0,
              1,
              TimeUnit.MILLISECONDS));
    }

    Thread.sleep(100);

    underTest.install(name -> new TestLogger(name, logMessages));

    Thread.sleep(100);

    futures.forEach(f -> f.cancel(true));

    assertThat(logMessages)
        .as("Verify that the application logger bridge did not lose any messages")
        .hasSize(count.get());
  }

  private static final class TestLogger extends InternalLogger {

    private final String name;
    private final List<Integer> logMessages;

    private TestLogger(String name, List<Integer> logMessages) {
      this.name = name;
      this.logMessages = logMessages;
    }

    @Override
    public boolean isLoggable(Level level) {
      return true;
    }

    @Override
    public void log(Level level, String message, Throwable error) {
      logMessages.add(Integer.parseInt(message));
    }

    @Override
    public String name() {
      return name;
    }
  }
}
