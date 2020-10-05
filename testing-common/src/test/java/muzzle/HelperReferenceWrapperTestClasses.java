/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package muzzle;

public class HelperReferenceWrapperTestClasses {
  interface Interface1 {
    void foo();
  }

  interface Interface2 {
    void bar();
  }

  abstract static class AbstractClasspathType implements Interface1 {
    static void staticMethodsAreIgnored() {}

    private void privateMethodsToo() {}
  }
}
