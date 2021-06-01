/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package muzzle;

@SuppressWarnings({"UnusedMethod", "MethodCanBeStatic"})
public class HelperReferenceWrapperTestClasses {
  interface Interface1 {
    void foo();
  }

  interface Interface2 {
    void bar();
  }

  abstract static class AbstractClasspathType implements Interface1 {
    private Object privateFieldsAreIgnored;
    protected Object field;

    static void staticMethodsAreIgnored() {}

    private void privateMethodsToo() {}
  }
}
