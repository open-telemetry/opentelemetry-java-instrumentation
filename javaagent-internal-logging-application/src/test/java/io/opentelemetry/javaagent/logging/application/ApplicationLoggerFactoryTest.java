/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.logging.application;

import static io.opentelemetry.javaagent.bootstrap.InternalLogger.Level.INFO;
import static io.opentelemetry.javaagent.bootstrap.InternalLogger.Level.WARN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.opentelemetry.javaagent.bootstrap.InternalLogger;
import io.opentelemetry.javaagent.bootstrap.logging.ApplicationLoggerBridge;
import io.opentelemetry.javaagent.tooling.internal.AgentCircularityLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ApplicationLoggerFactoryTest {

  @Mock InMemoryLogStore logStore;
  @Mock InternalLogger.Factory applicationLoggerBridge;
  @Mock InternalLogger applicationLogger;

  ApplicationLoggerFactory underTest;

  @BeforeEach
  void setUp() {
    underTest = new ApplicationLoggerFactory(logStore);
  }

  @Test
  void shouldNotDuplicateLoggers() {
    InternalLogger testLogger = underTest.create("test-logger");

    InternalLogger anotherLogger1 = underTest.create("another-logger");
    InternalLogger anotherLogger2 = underTest.create("another-logger");

    assertThat(testLogger).isNotSameAs(anotherLogger1).isNotSameAs(anotherLogger2);
    assertThat(anotherLogger1).isSameAs(anotherLogger2);
  }

  @Test
  void shouldOnlyInstallTheFirstBridge() {
    when(logStore.currentSize()).thenReturn(1, 0, 0);
    when(applicationLoggerBridge.create(any())).thenReturn(applicationLogger);

    underTest.install(applicationLoggerBridge);

    verify(logStore, times(3)).currentSize();
    // the agent must not call into the application logging system while a class file
    // transformation is in progress, so install() wraps the bridge before handing it to the
    // log store - see TransformSafeApplicationLoggerFactory
    verify(logStore).flush(isA(TransformSafeApplicationLoggerFactory.class));
    verify(logStore).setApplicationLoggerFactory(isA(TransformSafeApplicationLoggerFactory.class));
    verify(logStore).freeMemory();

    underTest.install(applicationLoggerBridge);

    // verify logged warning
    verify(applicationLoggerBridge).create(ApplicationLoggerBridge.class.getName());
    verify(applicationLogger).log(eq(WARN), anyString(), isNull());

    verifyNoMoreInteractions(logStore);
  }

  @Test
  void shouldReplaceLoggerAfterTheBridgeIsInstalled() {
    InternalLogger beforeInstall = underTest.create("logger");
    assertThat(beforeInstall).isInstanceOf(ApplicationLogger.class);

    beforeInstall.log(INFO, "before", null);
    verify(logStore).write(InMemoryLog.create("logger", INFO, "before", null));

    when(applicationLoggerBridge.create("logger")).thenReturn(applicationLogger);
    underTest.install(applicationLoggerBridge);

    // the logger created after install is no longer an in-memory one, it logs through the
    // application logging system - wrapped, see TransformSafeApplicationLoggerFactory
    InternalLogger afterInstall = underTest.create("logger");
    assertThat(afterInstall).isNotInstanceOf(ApplicationLogger.class);

    afterInstall.log(INFO, "direct", null);
    verify(applicationLogger).log(INFO, "direct", null);

    beforeInstall.log(INFO, "after", null);
    verify(applicationLogger).log(INFO, "after", null);
    verify(logStore, never()).write(InMemoryLog.create("logger", INFO, "after", null));
  }

  @Test
  void shouldNotCallApplicationLoggerWhenInstalledUnderLock() {
    InMemoryLogStore inMemoryLogStore = new InMemoryLogStore(10);
    ApplicationLoggerFactory factory = new ApplicationLoggerFactory(inMemoryLogStore);
    InternalLogger beforeInstall = factory.create("logger");
    beforeInstall.log(INFO, "buffered", null);
    assertThat(inMemoryLogStore.currentSize()).isOne();

    assertThat(AgentCircularityLock.INSTANCE.acquire()).isTrue();
    try {
      factory.install(applicationLoggerBridge);
      assertThat(beforeInstall.isLoggable(INFO)).isFalse();
      beforeInstall.log(INFO, "during transformation", null);
      verifyNoInteractions(applicationLoggerBridge, applicationLogger);
    } finally {
      AgentCircularityLock.INSTANCE.release();
    }

    assertThat(inMemoryLogStore.currentSize()).isZero();
    when(applicationLoggerBridge.create("logger")).thenReturn(applicationLogger);
    beforeInstall.log(INFO, "after", null);
    factory.create("logger").log(INFO, "direct", null);

    verify(applicationLoggerBridge, times(2)).create("logger");
    verify(applicationLogger).log(INFO, "after", null);
    verify(applicationLogger).log(INFO, "direct", null);
    verifyNoMoreInteractions(applicationLoggerBridge, applicationLogger);
  }

  @Test
  void shouldSuppressDuplicateInstallWarningUnderLock() {
    InternalLogger.Factory anotherBridge = mock(InternalLogger.Factory.class);
    underTest.install(applicationLoggerBridge);

    assertThat(AgentCircularityLock.INSTANCE.acquire()).isTrue();
    try {
      underTest.install(anotherBridge);
      verifyNoInteractions(applicationLoggerBridge, anotherBridge, applicationLogger);
    } finally {
      AgentCircularityLock.INSTANCE.release();
    }

    when(anotherBridge.create(ApplicationLoggerBridge.class.getName()))
        .thenReturn(applicationLogger);
    underTest.install(anotherBridge);
    verify(anotherBridge).create(ApplicationLoggerBridge.class.getName());
    verify(applicationLogger).log(eq(WARN), anyString(), isNull());

    when(applicationLoggerBridge.create("logger")).thenReturn(applicationLogger);
    underTest.create("logger").log(INFO, "first bridge", null);
    verify(applicationLoggerBridge).create("logger");
    verify(applicationLogger).log(INFO, "first bridge", null);
    verifyNoMoreInteractions(applicationLoggerBridge, anotherBridge, applicationLogger);
  }
}
