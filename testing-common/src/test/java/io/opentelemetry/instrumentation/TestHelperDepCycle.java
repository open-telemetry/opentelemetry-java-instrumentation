/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation;

public class TestHelperDepCycle {

  void referenceBothDirectly() {
    new ClassTwo();
    new ClassOne();
  }

  public static class ClassOne {
    void referenceSecondClass() {
      new ClassTwo();
    }

    void something() {}
  }

  public static class ClassTwo {
    void referenceFirstClass(ClassOne classOne) {
      classOne.something();
    }
  }
}
