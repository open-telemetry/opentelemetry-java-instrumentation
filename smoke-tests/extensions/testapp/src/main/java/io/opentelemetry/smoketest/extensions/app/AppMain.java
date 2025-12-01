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
    testVirtualFields();
    testLocalValue();
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
    // method return value should be modified by instrumentation
    return value;
  }

  private static void testMethodArguments() {
    methodArguments(42, 42);
  }

  private static void methodArguments(int argument, int originalArgument) {
    // method first argument should be modified by instrumentation
    if (argument != originalArgument) {
      System.out.println("argument has been modified");
    } else {
      System.out.println("argument not modified");
    }
  }

  private static void testVirtualFields() {
    Object target = new Object();
    setVirtualFieldValue(target, 42);
    Integer fieldValue = getVirtualFieldValue(target);
    if (fieldValue == null || fieldValue != 42) {
      System.out.println("virtual field not supported");
    } else {
      System.out.println("virtual field supported");
    }
  }

  public static void setVirtualFieldValue(Object target, Integer value) {
    // implementation should be provided by instrumentation
  }

  public static Integer getVirtualFieldValue(Object target) {
    // implementation should be provided by instrumentation
    return null;
  }

  private static void testLocalValue() {
    int[] input = new int[] {1, 2, 3};
    int result = localValue(input);
    if (result != 6) {
      throw new IllegalStateException();
    }
    // assumption on the instrumentation implementation to use a local value to preserve original array
    boolean preserved = input[0] == 1 && input[1] == 2 && input[2] == 3;
    if(!preserved) {
      System.out.println("local advice variable not supported");
    } else {
      System.out.println("local advice variable supported");
    }

  }

  private static int localValue(int[] array) {
    int sum = 0;
    for (int i = 0; i < array.length; i++) {
      sum += array[i];
      array[i] = 0;
    }
    return sum;
  }
}
