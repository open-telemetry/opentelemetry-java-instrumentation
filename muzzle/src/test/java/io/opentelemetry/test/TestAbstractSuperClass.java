/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.test;

public abstract class TestAbstractSuperClass {
  protected abstract int abstractMethod();

  public final String finalMethod() {
    return "42";
  }

  static int bar() {
    return 12345;
  }
}
