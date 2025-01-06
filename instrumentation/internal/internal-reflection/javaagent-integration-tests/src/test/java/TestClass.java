/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import java.io.Serializable;

public class TestClass implements Runnable, Serializable {

  @Override
  public void run() {}

  public String testMethod() {
    return "not instrumented";
  }

  public String testMethod2() {
    return "not instrumented";
  }

  public Class<?> testHelperClass() {
    try {
      return Class.forName(
          "instrumentation.TestHelperClass", false, TestClass.class.getClassLoader());
    } catch (ClassNotFoundException e) {
      return null;
    }
  }
}
