/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.logging.application;

import static io.opentelemetry.javaagent.bootstrap.InternalLogger.Level.INFO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.opentelemetry.javaagent.bootstrap.InternalLogger;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
    assertThat(out.toString(StandardCharsets.UTF_8.name())).hasLineCount(2);
  }
}
