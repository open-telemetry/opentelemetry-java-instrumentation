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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.opentelemetry.javaagent.bootstrap.InternalLogger;
import io.opentelemetry.javaagent.bootstrap.logging.ApplicationLoggerBridge;
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
    verify(logStore).flush(applicationLoggerBridge);
    verify(logStore).setApplicationLoggerFactory(applicationLoggerBridge);
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

    InternalLogger afterInstall = underTest.create("logger");
    assertThat(afterInstall).isSameAs(applicationLogger);

    beforeInstall.log(INFO, "after", null);
    verify(applicationLogger).log(INFO, "after", null);
    verify(logStore, never()).write(InMemoryLog.create("logger", INFO, "after", null));
  }
}
