/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest.extensions.app;

public class AppMain {

  private AppMain() {}

  public static void main(String[] args) {
    testReturnValue();
    testMethodArguments();
  }

  private static void testReturnValue() {
    int returnValue = returnValue(42);
    if (returnValue != 42) {
      System.out.println("return value has been modified");
    } else {
      System.out.println("return value not modified");
    }
  }

  private static int returnValue(int value) {
    return value;
  }

  private static void testMethodArguments() {
    methodArguments(42, 42);
  }

  private static void methodArguments(int argument, int originalArgument) {
    if (argument != originalArgument) {
      System.out.println("argument has been modified");
    } else {
      System.out.println("argument not modified");
    }
  }

}
