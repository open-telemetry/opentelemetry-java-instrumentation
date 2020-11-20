/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation;

import java.util.Arrays;
import java.util.List;

public enum TestHelperDeps {
  INSTANCE;

  private static final List<String> CONSTANT_OBJ = Arrays.asList("1", "2");

  FooProvider getFooProvider() {
    return FooProvider.INSTANCE;
  }

  BarProvider getBarProvider() {
    return BarProvider.INSTANCE;
  }

  ThirdOne create() {
    return new ThirdOne();
  }

  public enum FooProvider {
    INSTANCE;

    Foo getFoo() {
      return new Foo();
    }
  }

  public enum BarProvider {
    INSTANCE;

    Bar getBar() {
      return new Bar();
    }
  }

  public static class Foo {
    SomeTestClass create() {
      new Bar();
      return new SomeTestClass();
    }
  }

  public static class Bar {}

  public static class ThirdOne {}

  public static class SomeTestClass {
    void whatever() {
      new ThirdOne();
      TestHelperDeps.CONSTANT_OBJ.isEmpty();
      doSomething();
    }

    void doSomething() {}
  }
}
