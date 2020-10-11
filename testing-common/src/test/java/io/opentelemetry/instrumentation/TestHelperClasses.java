/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation;

import java.util.ArrayList;
import java.util.List;

public class TestHelperClasses {
  public static class Helper extends HelperSuperClass implements HelperInterface {

    @Override
    public void foo() {
      List<String> list = new ArrayList<>();
      list.add(getStr());
    }

    @Override
    protected int abstractMethod() {
      return 54321;
    }

    private String getStr() {
      return "abc";
    }
  }

  public interface HelperInterface {
    void foo();
  }

  public interface AnotherHelperInterface extends HelperInterface {
    void bar();

    int hashCode();

    boolean equals(Object other);

    Object clone();

    void finalize();
  }

  public abstract static class HelperSuperClass {
    protected abstract int abstractMethod();

    public final String finalMethod() {
      return "42";
    }

    static int bar() {
      return 12345;
    }
  }
}
