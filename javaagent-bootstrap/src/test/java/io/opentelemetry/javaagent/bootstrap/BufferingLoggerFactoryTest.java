/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.javaagent.bootstrap.InternalLogger.Level;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Test;

class BufferingLoggerFactoryTest {

  @Test
  void replaysBufferedRecordsInOrder() {
    BufferingLoggerFactory factory = new BufferingLoggerFactory(100);
    factory.create("a").log(Level.INFO, "one", null);
    factory.create("b").log(Level.WARN, "two", null);
    factory.create("a").log(Level.ERROR, "three", null);

    RecordingFactory target = new RecordingFactory(Level.TRACE);
    assertThat(factory.claim()).isTrue();
    factory.drainTo(target);

    assertThat(target.messages()).containsExactly("a INFO one", "b WARN two", "a ERROR three");
  }

  @Test
  void filtersByTargetLevelOnReplay() {
    BufferingLoggerFactory factory = new BufferingLoggerFactory(100);
    InternalLogger logger = factory.create("a");
    // the target level is unknown before initialization
    assertThat(logger.isLoggable(Level.TRACE)).isTrue();
    logger.log(Level.DEBUG, "debug", null);
    logger.log(Level.INFO, "info", null);

    RecordingFactory target = new RecordingFactory(Level.INFO);
    factory.claim();
    factory.drainTo(target);

    assertThat(target.messages()).containsExactly("a INFO info");
    assertThat(logger.isLoggable(Level.DEBUG)).isFalse();
  }

  @Test
  void loggerObtainedBeforeInitializationLogsToTarget() {
    BufferingLoggerFactory factory = new BufferingLoggerFactory(100);
    // PatchLogger holds on to the InternalLogger it was created with, like a static final logger
    PatchLogger patchLogger = new PatchLogger(factory.create("early"));

    RecordingFactory target = new RecordingFactory(Level.TRACE);
    factory.claim();
    factory.drainTo(target);
    patchLogger.warning("after init");

    assertThat(target.messages()).containsExactly("early WARN after init");
  }

  @Test
  void createAfterInitializationReturnsTargetLogger() {
    BufferingLoggerFactory factory = new BufferingLoggerFactory(100);
    RecordingFactory target = new RecordingFactory(Level.TRACE);
    factory.claim();
    factory.drainTo(target);

    assertThat(factory.create("late")).isInstanceOf(RecordingLogger.class);
  }

  @Test
  void dropsRecordsOverLimitAndReportsCount() {
    BufferingLoggerFactory factory = new BufferingLoggerFactory(2);
    InternalLogger logger = factory.create("a");
    logger.log(Level.INFO, "one", null);
    logger.log(Level.INFO, "two", null);
    logger.log(Level.INFO, "three", null);
    logger.log(Level.INFO, "four", null);

    RecordingFactory target = new RecordingFactory(Level.TRACE);
    factory.claim();
    factory.drainTo(target);

    assertThat(target.messages())
        .containsExactly(
            "a INFO one",
            "a INFO two",
            InternalLogger.class.getName()
                + " WARN Dropped 2 log records emitted before logging was initialized (buffer"
                + " limit 2)");
  }

  @Test
  void claimSucceedsOnce() {
    BufferingLoggerFactory factory = new BufferingLoggerFactory(100);
    assertThat(factory.claim()).isTrue();
    assertThat(factory.claim()).isFalse();
  }

  @Test
  void keepsOrderWhenReplayLogsAgain() {
    BufferingLoggerFactory factory = new BufferingLoggerFactory(100);
    InternalLogger early = factory.create("early");
    early.log(Level.INFO, "first", null);
    early.log(Level.INFO, "second", null);

    // the selected logger logs through an early logger while replaying, e.g. because replaying
    // loads a class that logs
    AtomicBoolean reentered = new AtomicBoolean();
    RecordingFactory target =
        new RecordingFactory(
            Level.TRACE,
            message -> {
              if (message.equals("early INFO first") && reentered.compareAndSet(false, true)) {
                early.log(Level.INFO, "during replay", null);
              }
            });
    factory.claim();
    factory.drainTo(target);

    assertThat(target.messages())
        .containsExactly("early INFO first", "early INFO second", "early INFO during replay");
  }

  @Test
  void throwingTargetDoesNotLeaveRecordsStuck() {
    BufferingLoggerFactory factory = new BufferingLoggerFactory(100);
    InternalLogger early = factory.create("early");
    early.log(Level.INFO, "boom", null);
    early.log(Level.INFO, "lost", null);

    RecordingFactory target =
        new RecordingFactory(
            Level.TRACE,
            message -> {
              if (message.endsWith("boom")) {
                throw new IllegalStateException("broken logger");
              }
            });
    factory.claim();
    assertThatThrownBy(() -> factory.drainTo(target)).isInstanceOf(IllegalStateException.class);

    early.log(Level.INFO, "after failure", null);
    assertThat(target.messages()).containsExactly("early INFO after failure");
  }

  @Test
  void dumpPrintsWarningsAndErrorsBeforeInitialization() {
    BufferingLoggerFactory factory = new BufferingLoggerFactory(100);
    InternalLogger logger = factory.create("a");
    logger.log(Level.INFO, "info", null);
    logger.log(Level.WARN, "warn", null);
    logger.log(Level.ERROR, "error", new IllegalStateException("cause"));

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    factory.dumpTo(new PrintStream(out, true));

    String printed = new String(out.toByteArray(), UTF_8);
    assertThat(printed)
        .contains("[otel.javaagent] WARN a - warn")
        .contains("[otel.javaagent] ERROR a - error")
        .contains("java.lang.IllegalStateException: cause")
        .doesNotContain("info");
  }

  @Test
  void dumpDoesNothingAfterInitialization() {
    BufferingLoggerFactory factory = new BufferingLoggerFactory(100);
    factory.create("a").log(Level.ERROR, "error", null);
    factory.claim();
    factory.drainTo(new RecordingFactory(Level.TRACE));

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    factory.dumpTo(new PrintStream(out, true));

    assertThat(new String(out.toByteArray(), UTF_8)).isEmpty();
  }

  @Test
  void noRecordsLostWhenLoggingConcurrentlyWithInitialization() throws Exception {
    // the race is between a writer seeing "not initialized yet" and taking the buffer lock, so
    // repeat to make hitting it likely
    for (int round = 0; round < 20; round++) {
      logConcurrentlyWithInitialization();
    }
  }

  private static void logConcurrentlyWithInitialization() throws Exception {
    int threads = 4;
    int recordsPerThread = 5_000;
    BufferingLoggerFactory factory = new BufferingLoggerFactory(threads * recordsPerThread);
    RecordingFactory target = new RecordingFactory(Level.TRACE);
    // initialize only once every writer is busy, so initialization overlaps with logging
    CountDownLatch writing = new CountDownLatch(threads);

    List<Thread> writers = new ArrayList<>();
    for (int i = 0; i < threads; i++) {
      InternalLogger logger = factory.create("thread-" + i);
      Thread writer =
          new Thread(() -> {
            for (int j = 0; j < recordsPerThread; j++) {
              logger.log(Level.INFO, Integer.toString(j), null);
              if (j == 100) {
                writing.countDown();
              }
            }
          });
      writer.start();
      writers.add(writer);
    }

    writing.await();
    factory.claim();
    factory.drainTo(target);
    for (Thread writer : writers) {
      writer.join();
    }

    List<String> messages = target.messages();
    assertThat(messages).hasSize(threads * recordsPerThread);
    // each thread's records stay in the order they were logged
    for (int i = 0; i < threads; i++) {
      String prefix = "thread-" + i + " INFO ";
      List<String> ownRecords = new ArrayList<>();
      for (String message : messages) {
        if (message.startsWith(prefix)) {
          ownRecords.add(message.substring(prefix.length()));
        }
      }
      assertThat(ownRecords).hasSize(recordsPerThread);
      for (int j = 0; j < recordsPerThread; j++) {
        assertThat(ownRecords.get(j)).isEqualTo(Integer.toString(j));
      }
    }
  }

  private static final class RecordingFactory implements InternalLogger.Factory {

    private final Set<Level> enabledLevels;
    private final Consumer<String> onLog;
    private final List<String> messages = Collections.synchronizedList(new ArrayList<>());

    RecordingFactory(Level minLevel) {
      this(minLevel, message -> {});
    }

    RecordingFactory(Level minLevel, Consumer<String> onLog) {
      // Level is declared from most to least severe
      this.enabledLevels = EnumSet.range(Level.ERROR, minLevel);
      this.onLog = onLog;
    }

    @Override
    public InternalLogger create(String name) {
      return new RecordingLogger(this, name);
    }

    List<String> messages() {
      synchronized (messages) {
        return new ArrayList<>(messages);
      }
    }
  }

  private static final class RecordingLogger implements InternalLogger {

    private final RecordingFactory factory;
    private final String name;

    RecordingLogger(RecordingFactory factory, String name) {
      this.factory = factory;
      this.name = name;
    }

    @Override
    public boolean isLoggable(Level level) {
      return factory.enabledLevels.contains(level);
    }

    @Override
    public void log(Level level, String message, @Nullable Throwable error) {
      String formatted = name + " " + level + " " + message;
      factory.onLog.accept(formatted);
      factory.messages.add(formatted);
    }

    @Override
    public String name() {
      return name;
    }
  }
}
