/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.appender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    assertThatThrownBy(() -> GlobalLogEmitterProvider.set(NoopLogEmitterProvider.INSTANCE))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("GlobalLogEmitterProvider.set has already been called")
        .hasStackTraceContaining("setLogEmitterProvider");
  }

  @Test
  void getThenSet() {
    assertThat(getLogEmitterProvider()).isInstanceOf(NoopLogEmitterProvider.class);
    assertThatThrownBy(() -> GlobalLogEmitterProvider.set(NoopLogEmitterProvider.INSTANCE))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("GlobalLogEmitterProvider.set has already been called")
        .hasStackTraceContaining("getLogEmitterProvider");
  }

  private static void setLogEmitterProvider() {
    GlobalLogEmitterProvider.set(NoopLogEmitterProvider.INSTANCE);
  }

  private static LogEmitterProvider getLogEmitterProvider() {
    return GlobalLogEmitterProvider.get();
  }
}
