/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.javaconcurrent;

import static org.assertj.core.api.Assertions.assertThatCode;

import javax.swing.RepaintManager;
import org.junit.jupiter.api.Test;

// This class tests that we correctly add module references when instrumenting
class ModuleInjectionTest {

  // There's nothing special about RepaintManager other than it's in a module (java.desktop) that
  // doesn't read the "unnamed module" and it creates an instrumented runnable in its constructor.
  @Test
  void instrumentingJavaDesktopClass() {
    assertThatCode(RepaintManager::new).doesNotThrowAnyException();
  }
}
