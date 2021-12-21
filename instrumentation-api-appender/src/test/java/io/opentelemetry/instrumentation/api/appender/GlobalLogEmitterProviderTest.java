/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.appender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class GlobalLogEmitterProviderTest {

  @BeforeAll
  static void beforeClass() {
    GlobalLogEmitterProvider.resetForTest();
  }

  @AfterEach
  void after() {
    GlobalLogEmitterProvider.resetForTest();
  }

  @Test
  void testGlobalBeforeSet() {
    assertThat(GlobalLogEmitterProvider.get()).isSameAs(NoopLogEmitterProvider.INSTANCE);
  }

  @Test
  void setThenSet() {
    setLogEmitterProvider();
    assertThatThrownBy(() -> GlobalLogEmitterProvider.set(mock(LogEmitterProvider.class)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("GlobalLogEmitterProvider.set has already been called")
        .hasStackTraceContaining("setLogEmitterProvider");
  }

  @Test
  void getThenSet() {
    LogEmitterProvider existingProvider = GlobalLogEmitterProvider.get();
    assertSame(existingProvider, NoopLogEmitterProvider.INSTANCE);
    LogEmitterProvider newProvider = mock(LogEmitterProvider.class);
    GlobalLogEmitterProvider.set(newProvider);
    assertSame(newProvider, GlobalLogEmitterProvider.get());
  }

  @Test
  void okToSetNoopMultipleTimes() {
    GlobalLogEmitterProvider.set(NoopLogEmitterProvider.INSTANCE);
    GlobalLogEmitterProvider.set(NoopLogEmitterProvider.INSTANCE);
    GlobalLogEmitterProvider.set(NoopLogEmitterProvider.INSTANCE);
    GlobalLogEmitterProvider.set(NoopLogEmitterProvider.INSTANCE);
    // pass
  }

  private static void setLogEmitterProvider() {
    GlobalLogEmitterProvider.set(mock(LogEmitterProvider.class));
  }
}
