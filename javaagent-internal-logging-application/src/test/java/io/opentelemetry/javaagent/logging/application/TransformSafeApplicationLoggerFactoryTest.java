/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.logging.application;

import static io.opentelemetry.javaagent.bootstrap.InternalLogger.Level.INFO;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.javaagent.bootstrap.InternalLogger;
import io.opentelemetry.javaagent.bootstrap.internal.InTransformation;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TransformSafeApplicationLoggerFactoryTest {

  @AfterEach
  void leaveTransformation() {
    while (InTransformation.get()) {
      InTransformation.exit();
    }
  }

  @Test
  void shouldLogDirectlyWhenNotInTransformation() throws Exception {
    RecordingFactory delegate = new RecordingFactory();
    InternalLogger logger = new TransformSafeApplicationLoggerFactory(delegate).create("test");

    logger.log(INFO, "a", null);

    assertThat(delegate.await()).isTrue();
    assertThat(delegate.message.get()).isEqualTo("a");
    assertThat(delegate.loggedOn.get()).isSameAs(Thread.currentThread());
  }

  @Test
  void shouldNotCallApplicationLoggerOnAThreadInsideATransformation() throws Exception {
    RecordingFactory delegate = new RecordingFactory();
    InternalLogger logger = new TransformSafeApplicationLoggerFactory(delegate).create("test");

    InTransformation.enter();
    logger.log(INFO, "a", null);

    // the record is not lost, it is just emitted from somewhere other than the transforming thread
    assertThat(delegate.await()).isTrue();
    assertThat(delegate.message.get()).isEqualTo("a");
    assertThat(delegate.loggedOn.get()).isNotSameAs(Thread.currentThread());
  }

  @Test
  void shouldNotCreateTheBridgedLoggerOnAThreadInsideATransformation() throws Exception {
    RecordingFactory delegate = new RecordingFactory();
    InternalLogger logger = new TransformSafeApplicationLoggerFactory(delegate).create("test");

    InTransformation.enter();
    logger.log(INFO, "a", null);

    // creating the bridged logger calls into the application logging system as well, so it must
    // not happen on the transforming thread either
    assertThat(delegate.await()).isTrue();
    assertThat(delegate.createdOn.get()).isNotSameAs(Thread.currentThread());
  }

  @Test
  void shouldKeepDrainingAfterTheApplicationLoggerThrows() throws Exception {
    RecordingFactory delegate = new RecordingFactory();
    InternalLogger logger = new TransformSafeApplicationLoggerFactory(delegate).create("test");
    delegate.failNext.set(true);

    InTransformation.enter();
    logger.log(INFO, "boom", null);
    logger.log(INFO, "after", null);

    // the first record blows up in the drain thread, the second one still gets through
    assertThat(delegate.await()).isTrue();
    assertThat(delegate.message.get()).isEqualTo("after");
  }

  @Test
  void shouldAlwaysBeLoggableWhileInTransformation() {
    RecordingFactory delegate = new RecordingFactory();
    InternalLogger logger = new TransformSafeApplicationLoggerFactory(delegate).create("test");

    InTransformation.enter();

    // the application logging system can't be consulted here, so the record has to be built
    assertThat(logger.isLoggable(INFO)).isTrue();
    assertThat(delegate.createdOn.get()).isNull();
  }

  private static final class RecordingFactory implements InternalLogger.Factory {

    final AtomicBoolean failNext = new AtomicBoolean();
    final AtomicReference<Thread> createdOn = new AtomicReference<>();
    final AtomicReference<Thread> loggedOn = new AtomicReference<>();
    final AtomicReference<String> message = new AtomicReference<>();
    final CountDownLatch logged = new CountDownLatch(1);

    boolean await() throws InterruptedException {
      return logged.await(10, SECONDS);
    }

    @Override
    public InternalLogger create(String name) {
      createdOn.set(Thread.currentThread());
      return new InternalLogger() {
        @Override
        public boolean isLoggable(Level level) {
          return true;
        }

        @Override
        public void log(Level level, String message, Throwable error) {
          if (RecordingFactory.this.failNext.compareAndSet(true, false)) {
            throw new IllegalStateException("application logger failed");
          }
          loggedOn.set(Thread.currentThread());
          RecordingFactory.this.message.set(message);
          logged.countDown();
        }

        @Override
        public String name() {
          return name;
        }
      };
    }
  }
}
