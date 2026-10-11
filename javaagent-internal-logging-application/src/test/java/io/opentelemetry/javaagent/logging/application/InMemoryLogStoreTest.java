/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.logging.application;

import static io.opentelemetry.javaagent.bootstrap.InternalLogger.Level.INFO;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.opentelemetry.javaagent.bootstrap.InternalLogger;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InMemoryLogStoreTest {

  @Mock InternalLogger.Factory applicationLoggerBridge;
  @Mock InternalLogger applicationLogger;

  ExecutorService anotherThread = Executors.newSingleThreadExecutor();

  @AfterEach
  void tearDown() {
    anotherThread.shutdownNow();
  }

  @Test
  void shouldWriteLog() {
    InMemoryLogStore underTest = new InMemoryLogStore(42);
    InMemoryLog log = InMemoryLog.create("test-logger", INFO, "a", null);

    underTest.write(log);

    assertThat(underTest.copyLogs()).containsExactly(log);
  }

  @Test
  void shouldNotWriteLogsOverLimit() {
    InMemoryLogStore underTest = new InMemoryLogStore(2);
    InMemoryLog log = InMemoryLog.create("test-logger", INFO, "a", null);

    underTest.write(log);
    underTest.write(log);
    underTest.write(log);

    assertThat(underTest.copyLogs()).hasSize(2);
  }

  @Test
  void shouldForwardLogAfterFactoryIsInstalled() {
    InMemoryLogStore underTest = new InMemoryLogStore(0);
    Throwable error = new IllegalStateException("test");
    InMemoryLog log = InMemoryLog.create("test-logger", INFO, "a", error);
    when(applicationLoggerBridge.create("test-logger")).thenReturn(applicationLogger);
    underTest.setApplicationLoggerFactory(applicationLoggerBridge);

    underTest.write(log);

    verify(applicationLogger).log(INFO, "a", error);
    assertThat(underTest.copyLogs()).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void shouldAllowStoreAccessWhileApplicationLoggingIsBlocked(boolean blockCreate)
      throws Exception {
    InMemoryLogStore underTest = new InMemoryLogStore(42);
    InMemoryLog log = InMemoryLog.create("test-logger", INFO, "a", null);
    CountDownLatch callbackStarted = new CountDownLatch(1);
    CountDownLatch releaseCallback = new CountDownLatch(1);
    when(applicationLoggerBridge.create("test-logger"))
        .thenAnswer(
            invocation -> {
              if (blockCreate) {
                callbackStarted.countDown();
                assertThat(releaseCallback.await(10, SECONDS)).isTrue();
              }
              return applicationLogger;
            });
    doAnswer(
            invocation -> {
              if (!blockCreate) {
                callbackStarted.countDown();
                assertThat(releaseCallback.await(10, SECONDS)).isTrue();
              }
              return null;
            })
        .when(applicationLogger)
        .log(INFO, "a", null);
    underTest.setApplicationLoggerFactory(applicationLoggerBridge);

    ExecutorService threads = Executors.newFixedThreadPool(2);
    try {
      Future<?> write = threads.submit(() -> underTest.write(log));
      assertThat(callbackStarted.await(10, SECONDS)).isTrue();

      assertThat(threads.submit(underTest::currentSize).get(5, SECONDS)).isZero();
      assertThat(write.isDone()).isFalse();

      releaseCallback.countDown();
      write.get(10, SECONDS);
      verify(applicationLogger).log(INFO, "a", null);
      assertThat(underTest.copyLogs()).isEmpty();
    } finally {
      releaseCallback.countDown();
      threads.shutdownNow();
      assertThat(threads.awaitTermination(10, SECONDS)).isTrue();
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void shouldPropagateApplicationLoggingException(boolean failCreate) {
    InMemoryLogStore underTest = new InMemoryLogStore(42);
    InMemoryLog log = InMemoryLog.create("test-logger", INFO, "a", null);
    RuntimeException error = new IllegalStateException("test");
    if (failCreate) {
      when(applicationLoggerBridge.create("test-logger")).thenThrow(error);
    } else {
      when(applicationLoggerBridge.create("test-logger")).thenReturn(applicationLogger);
      doThrow(error).when(applicationLogger).log(INFO, "a", null);
    }
    underTest.setApplicationLoggerFactory(applicationLoggerBridge);

    assertThatThrownBy(() -> underTest.write(log)).isSameAs(error);
    assertThat(underTest.copyLogs()).isEmpty();
  }

  @Test
  void shouldFlush() {
    InMemoryLogStore underTest = new InMemoryLogStore(42);
    InMemoryLog log = InMemoryLog.create("test-logger", INFO, "a", null);

    underTest.write(log);
    underTest.write(log);
    assertThat(underTest.copyLogs()).hasSize(2);

    when(applicationLoggerBridge.create("test-logger")).thenReturn(applicationLogger);
    underTest.flush(applicationLoggerBridge);
    verify(applicationLogger, times(2)).log(INFO, "a", null);

    assertThat(underTest.copyLogs()).isEmpty();
  }

  @Test
  void shouldDumpLogs() throws UnsupportedEncodingException {
    InMemoryLogStore underTest = new InMemoryLogStore(42);
    InMemoryLog log = InMemoryLog.create("test-logger", INFO, "a", null);

    underTest.write(log);
    underTest.write(log);

    ByteArrayOutputStream out = new ByteArrayOutputStream();

    underTest.dump(new PrintStream(out));
    assertThat(out.toString(UTF_8.name())).hasLineCount(2);
  }
}
