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

class LogEmitterProviderHolderTest {

  private static final LogEmitterProviderHolder holder = new LogEmitterProviderHolder();

  @BeforeAll
  static void beforeClass() {
    holder.resetForTest();
  }

  @AfterEach
  void after() {
    holder.resetForTest();
  }

  @Test
  void testGlobalBeforeSet() {
    assertThat(holder.get()).isSameAs(NoopLogEmitterProvider.INSTANCE);
  }

  @Test
  void setThenSet() {
    setLogEmitterProvider();
    assertThatThrownBy(() -> holder.set(mock(LogEmitterProvider.class)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("LogEmitterProviderHolder.set has already been called")
        .hasStackTraceContaining("setLogEmitterProvider");
  }

  @Test
  void getThenSet() {
    LogEmitterProvider existingProvider = holder.get();
    assertSame(existingProvider, NoopLogEmitterProvider.INSTANCE);
    LogEmitterProvider newProvider = mock(LogEmitterProvider.class);
    holder.set(newProvider);
    assertSame(newProvider, holder.get());
  }

  @Test
  void okToSetNoopMultipleTimes() {
    holder.set(NoopLogEmitterProvider.INSTANCE);
    holder.set(NoopLogEmitterProvider.INSTANCE);
    holder.set(NoopLogEmitterProvider.INSTANCE);
    holder.set(NoopLogEmitterProvider.INSTANCE);
    // pass
  }

  private static void setLogEmitterProvider() {
    holder.set(mock(LogEmitterProvider.class));
  }
}
