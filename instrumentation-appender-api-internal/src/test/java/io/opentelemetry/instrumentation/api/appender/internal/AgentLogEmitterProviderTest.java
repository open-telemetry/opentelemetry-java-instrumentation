/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.appender.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class AgentLogEmitterProviderTest {

  @BeforeAll
  static void beforeClass() {
    AgentLogEmitterProvider.resetForTest();
  }

  @AfterEach
  void after() {
    AgentLogEmitterProvider.resetForTest();
  }

  @Test
  void testGlobalBeforeSet() {
    assertThat(AgentLogEmitterProvider.get()).isSameAs(NoopLogEmitterProvider.INSTANCE);
  }

  @Test
  void setThenSet() {
    setLogEmitterProvider();
    assertThatThrownBy(() -> AgentLogEmitterProvider.set(mock(LogEmitterProvider.class)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("LogEmitterProviderHolder.set has already been called")
        .hasStackTraceContaining("setLogEmitterProvider");
  }

  @Test
  void getThenSet() {
    LogEmitterProvider existingProvider = AgentLogEmitterProvider.get();
    assertSame(existingProvider, NoopLogEmitterProvider.INSTANCE);
    LogEmitterProvider newProvider = mock(LogEmitterProvider.class);
    AgentLogEmitterProvider.set(newProvider);
    assertSame(newProvider, AgentLogEmitterProvider.get());
  }

  @Test
  void okToSetNoopMultipleTimes() {
    AgentLogEmitterProvider.set(NoopLogEmitterProvider.INSTANCE);
    AgentLogEmitterProvider.set(NoopLogEmitterProvider.INSTANCE);
    AgentLogEmitterProvider.set(NoopLogEmitterProvider.INSTANCE);
    AgentLogEmitterProvider.set(NoopLogEmitterProvider.INSTANCE);
    // pass
  }

  private static void setLogEmitterProvider() {
    AgentLogEmitterProvider.set(mock(LogEmitterProvider.class));
  }
}
