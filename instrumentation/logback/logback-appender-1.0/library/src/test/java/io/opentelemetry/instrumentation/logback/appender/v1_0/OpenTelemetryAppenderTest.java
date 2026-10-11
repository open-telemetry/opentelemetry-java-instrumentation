/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.logback.appender.v1_0;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.LoggingEvent;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.logs.LogRecordProcessor;
import io.opentelemetry.sdk.logs.ReadWriteLogRecord;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class OpenTelemetryAppenderTest extends AbstractOpenTelemetryAppenderTest {

  @RegisterExtension
  private static final LibraryInstrumentationExtension testing =
      LibraryInstrumentationExtension.create();

  @BeforeEach
  void setup() {
    OpenTelemetryAppender.install(testing.getOpenTelemetry());
  }

  @AfterEach
  void cleanup() {
    OpenTelemetryAppender.resetForTest();
  }

  @Override
  protected InstrumentationExtension getTesting() {
    return testing;
  }

  @Test
  void logWithSpan() { // Does not work for log replay but it is not likely to occur because
    // the log replay is related to the case where an OpenTelemetry object is not yet available
    // at the time the log is executed (and if no OpenTelemetry is available, the context
    // propagation can't happen)
    Span span1 =
        testing.runWithSpan(
            "span1",
            () -> {
              logger.info("log message 1");
              return Span.current();
            });

    logger.info("log message 2");

    executeAfterLogsExecution();

    Span span2 =
        testing.runWithSpan(
            "span2",
            () -> {
              logger.info("log message 3");
              return Span.current();
            });

    testing.waitAndAssertLogRecords(
        logRecord -> logRecord.hasSpanContext(span1.getSpanContext()),
        logRecord -> logRecord.hasSpanContext(SpanContext.getInvalid()),
        logRecord -> logRecord.hasSpanContext(span2.getSpanContext()));
  }

  @Test
  void emissionDoesNotHoldReadLock() throws Exception {
    OpenTelemetryAppender appender = new OpenTelemetryAppender();
    appender.setName("OpenTelemetryAppender");
    appender.start();

    CountDownLatch setterCompleted = new CountDownLatch(1);
    AtomicBoolean setterCompletedDuringEmit = new AtomicBoolean();
    AtomicInteger emittedRecords = new AtomicInteger();
    ExecutorService setterExecutor = Executors.newSingleThreadExecutor();
    OpenTelemetrySdk openTelemetry =
        OpenTelemetrySdk.builder()
            .setLoggerProvider(
                SdkLoggerProvider.builder()
                    .addLogRecordProcessor(
                        new LogRecordProcessor() {
                          @Override
                          public void onEmit(Context context, ReadWriteLogRecord logRecord) {
                            emittedRecords.incrementAndGet();
                            setterExecutor.execute(
                                () -> {
                                  try {
                                    appender.setOpenTelemetry(testing.getOpenTelemetry());
                                  } finally {
                                    setterCompleted.countDown();
                                  }
                                });
                            try {
                              setterCompletedDuringEmit.set(setterCompleted.await(5, SECONDS));
                            } catch (InterruptedException e) {
                              Thread.currentThread().interrupt();
                              throw new AssertionError(e);
                            }
                          }

                          @Override
                          public CompletableResultCode forceFlush() {
                            return CompletableResultCode.ofSuccess();
                          }

                          @Override
                          public CompletableResultCode shutdown() {
                            return CompletableResultCode.ofSuccess();
                          }
                        })
                    .build())
            .build();

    ReentrantReadWriteLock lock = getLock(appender);
    AtomicReference<Throwable> appendFailure = new AtomicReference<>();
    Thread appendThread =
        new Thread(
            () -> {
              try {
                appender.doAppend(
                    new LoggingEvent(
                        OpenTelemetryAppenderTest.class.getName(),
                        (Logger) logger,
                        Level.INFO,
                        "log message",
                        null,
                        null));
              } catch (Throwable t) {
                appendFailure.set(t);
              }
            });
    appendThread.setDaemon(true);

    try {
      lock.writeLock().lock();
      try {
        appendThread.start();
        await().atMost(Duration.ofSeconds(5)).until(() -> lock.hasQueuedThread(appendThread));
        appender.setOpenTelemetry(openTelemetry);
      } finally {
        lock.writeLock().unlock();
      }
      appendThread.join(10_000);
      assertThat(appendThread.isAlive()).isFalse();
      assertThat(appendFailure.get()).isNull();
      assertThat(setterCompletedDuringEmit.get()).isTrue();
      assertThat(emittedRecords.get()).isEqualTo(1);
    } finally {
      if (appendThread.isAlive()) {
        appendThread.join(10_000);
      }
      appender.stop();
      openTelemetry.close();
      setterExecutor.shutdownNow();
      setterExecutor.awaitTermination(5, SECONDS);
    }
  }

  private static ReentrantReadWriteLock getLock(OpenTelemetryAppender appender)
      throws ReflectiveOperationException {
    Field lockField = OpenTelemetryAppender.class.getDeclaredField("lock");
    lockField.setAccessible(true);
    return (ReentrantReadWriteLock) lockField.get(appender);
  }
}
