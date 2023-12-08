/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package muzzle.other;

import muzzle.TestClasses;

// to testing protected methods we need to have a test classes in different packages
@SuppressWarnings("unused")
public class OtherTestClasses {

  @SuppressWarnings("ClassNamedLikeTypeParameter")
  public static class Nested {
    public static class B2 extends TestClasses.Nested.B {
      public void stuff() {
        super.protectedMethod();
      }
    }

    private Nested() {}
  }

  private OtherTestClasses() {}
}
