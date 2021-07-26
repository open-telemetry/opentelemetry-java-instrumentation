/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation;

import muzzle.TestClasses;

public class OtherTestHelperClasses {
  public static class Foo implements TestClasses.MethodBodyAdvice.SomeInterface {
    @Override
    public void someMethod() {}
  }

  public static class Bar {
    public void doSomething() {
      new Foo().someMethod();
      TestEnum.INSTANCE.getAnswer();
    }
  }

  public enum TestEnum {
    INSTANCE {
      @Override
      int getAnswer() {
        return 42;
      }
    };

    abstract int getAnswer();
  }
}
