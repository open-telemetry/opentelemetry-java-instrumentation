/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.logging.application;

import static io.opentelemetry.javaagent.bootstrap.InternalLogger.Level.INFO;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.opentelemetry.javaagent.bootstrap.InternalLogger;
import io.opentelemetry.javaagent.tooling.internal.AgentCircularityLock;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransformSafeApplicationLoggerFactoryTest {

  @Mock InternalLogger.Factory delegate;
  @Mock InternalLogger applicationLogger;

  @Test
  void createsDelegateLazily() {
    InternalLogger logger = new TransformSafeApplicationLoggerFactory(delegate).create("test");

    assertThat(logger.name()).isEqualTo("test");
    verifyNoInteractions(delegate);
  }

  @Test
  void logsAndChecksLevelOutsideLock() {
    when(delegate.create("test")).thenReturn(applicationLogger);
    when(applicationLogger.isLoggable(INFO)).thenReturn(true);
    InternalLogger logger = new TransformSafeApplicationLoggerFactory(delegate).create("test");
    Throwable error = new IllegalStateException("test");

    assertThat(logger.isLoggable(INFO)).isTrue();
    logger.log(INFO, "message", error);

    verify(delegate).create("test");
    verify(applicationLogger).isLoggable(INFO);
    verify(applicationLogger).log(INFO, "message", error);
    verifyNoMoreInteractions(delegate, applicationLogger);
  }

  @Test
  void doesNotCreateOrCallDelegateUnderLock() {
    assertThat(AgentCircularityLock.INSTANCE.acquire()).isTrue();
    try {
      InternalLogger logger = new TransformSafeApplicationLoggerFactory(delegate).create("test");
      assertThat(logger.name()).isEqualTo("test");
      assertThat(logger.isLoggable(INFO)).isFalse();
      logger.log(INFO, "suppressed", null);
      verifyNoInteractions(delegate, applicationLogger);
    } finally {
      AgentCircularityLock.INSTANCE.release();
    }
  }

  @Test
  void doesNotCallInitializedDelegateUnderLock() {
    when(delegate.create("test")).thenReturn(applicationLogger);
    InternalLogger logger = new TransformSafeApplicationLoggerFactory(delegate).create("test");
    logger.log(INFO, "before", null);
    verify(delegate).create("test");
    verify(applicationLogger).log(INFO, "before", null);

    assertThat(AgentCircularityLock.INSTANCE.acquire()).isTrue();
    try {
      assertThat(logger.isLoggable(INFO)).isFalse();
      logger.log(INFO, "suppressed", null);
      verifyNoMoreInteractions(delegate, applicationLogger);
    } finally {
      AgentCircularityLock.INSTANCE.release();
    }

    logger.log(INFO, "after", null);
    verify(applicationLogger).log(INFO, "after", null);
    verifyNoMoreInteractions(delegate, applicationLogger);
  }

  @Test
  void doesNotSuppressOtherThreads() throws Exception {
    when(delegate.create("test")).thenReturn(applicationLogger);
    when(applicationLogger.isLoggable(INFO)).thenReturn(true);
    InternalLogger logger = new TransformSafeApplicationLoggerFactory(delegate).create("test");

    assertThat(AgentCircularityLock.INSTANCE.acquire()).isTrue();
    try {
      CompletableFuture.runAsync(
              () -> {
                assertThat(logger.isLoggable(INFO)).isTrue();
                logger.log(INFO, "other thread", null);
              })
          .get(10, SECONDS);
      verify(delegate).create("test");
      verify(applicationLogger).isLoggable(INFO);
      verify(applicationLogger).log(INFO, "other thread", null);
      verifyNoMoreInteractions(delegate, applicationLogger);
    } finally {
      AgentCircularityLock.INSTANCE.release();
    }
  }
}
