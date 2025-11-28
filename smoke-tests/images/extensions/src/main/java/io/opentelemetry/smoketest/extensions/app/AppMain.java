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
      printMsg("return value has been modified");
    } else {
      printMsg("return value not modified");
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
      printMsg("argument has been modified");
    } else {
      printMsg("argument not modified");
    }
  }

  private static void printMsg(String msg) {
    // using a known prefix to allow easy filtering of expected output in test
    System.out.println(">>> " + msg);
  }
}
