/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.logging.application;

import static io.opentelemetry.javaagent.bootstrap.InternalLogger.Level.DEBUG;
import static io.opentelemetry.javaagent.bootstrap.InternalLogger.Level.INFO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.opentelemetry.javaagent.bootstrap.InternalLogger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ApplicationLoggerTest {

  @Mock InMemoryLogStore logStore;
  @Mock InternalLogger actualLogger;

  @ParameterizedTest
  @EnumSource(InternalLogger.Level.class)
  void shouldCaptureAllLogLevelsInMemory(InternalLogger.Level level) {
    ApplicationLogger underTest = new ApplicationLogger(logStore, "test");
    assertThat(underTest.isLoggable(level)).isTrue();
  }

  @Test
  void shouldStoreLogsInMemory() {
    ApplicationLogger underTest = new ApplicationLogger(logStore, "test");
    assertThat(underTest.name()).isEqualTo("test");

    underTest.log(INFO, "a", null);

    verify(logStore).write(new InMemoryLog("test", INFO, "a", null));
  }

  @Test
  void shouldReplaceTheLogger() {
    ApplicationLogger underTest = new ApplicationLogger(logStore, "test");

    underTest.replaceByActualLogger(actualLogger);

    when(actualLogger.isLoggable(DEBUG)).thenReturn(false);
    assertThat(underTest.isLoggable(DEBUG)).isFalse();

    underTest.log(INFO, "a", null);

    verify(actualLogger).log(INFO, "a", null);
    verifyNoMoreInteractions(logStore);
  }
}
