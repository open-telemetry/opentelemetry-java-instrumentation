/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest.extensions.app;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.io.PrintStream;
import java.util.Arrays;

public class AppMain {

  private AppMain() {}

  public static void main(String[] args) {
    testReturnValue();
    testMethodArguments();
    testVirtualFields();
    testLocalValue();
  }

  private static void msg(String msg) {
    // avoid checkstyle to complain
    PrintStream out = System.out;
    out.println(msg);
  }

  private static void testReturnValue() {
    int returnValue = returnValue(42);
    if (returnValue != 42) {
      msg("return value has been modified");
    } else {
      msg("return value not modified");
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
      msg("argument has been modified");
    } else {
      msg("argument not modified");
    }
  }

  private static void testVirtualFields() {
    Object target = new Object();
    setVirtualFieldValue(target, 42);
    Integer fieldValue = getVirtualFieldValue(target);
    if (fieldValue == null || fieldValue != 42) {
      msg("virtual field not supported");
    } else {
      msg("virtual field supported");
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
    int[] result = localValue(input);
    if (result.length != 3) {
      throw new IllegalStateException();
    }
    // assumption on the instrumentation implementation to use a local value to preserve original
    // array
    boolean preserved = result[0] == 1 && result[1] == 2 && result[2] == 3;
    if (!preserved) {
      msg("local advice variable not supported");
    } else {
      msg("local advice variable supported");
    }
  }

  @CanIgnoreReturnValue
  private static int[] localValue(int[] array) {
    Arrays.fill(array, 0);
    return array;
  }
}
