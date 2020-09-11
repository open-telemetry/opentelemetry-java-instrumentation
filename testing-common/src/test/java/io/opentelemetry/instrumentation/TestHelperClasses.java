/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
