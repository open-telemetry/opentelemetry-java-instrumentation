/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class TestHelperClasses {
  public static class Helper extends HelperSuperClass implements HelperInterface {

    @Override
    @SuppressWarnings("ModifiedButNotUsed")
    public void foo() {
      List<String> list = new ArrayList<>();
      list.add(getStr());
    }

    @Override
    protected int abstractMethod() {
      return 54321;
    }

    private static String getStr() {
      return "abc";
    }
  }

  public interface HelperInterface {
    void foo();
  }

  public interface AnotherHelperInterface extends HelperInterface {
    void bar();

    @Override
    int hashCode();

    @Override
    boolean equals(@Nullable Object other);

    Object clone();

    @SuppressWarnings("checkstyle:NoFinalizer")
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
