/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ClassNamesTest {

  @Test
  void testNamed() {
    assertThat(ClassNames.simpleName(Outer.class)).isEqualTo("Outer");
    assertThat(ClassNames.simpleName(Outer.Inner.class)).isEqualTo("Inner");
  }

  @Test
  void testAnonymous() {
    Runnable x =
        new Runnable() {
          @Override
          public void run() {}
        };
    assertThat(ClassNames.simpleName(x.getClass())).isEqualTo("ClassNamesTest$1");
  }

  @Test
  void testLambda() {
    Runnable x = () -> {};
    assertThat(ClassNames.simpleName(x.getClass())).startsWith("ClassNamesTest$$Lambda");
  }

  static class Outer {

    static class Inner {
      private Inner() {}
    }

    private Outer() {}
  }
}
