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
    // TODO: wip, does not work yet
    // testVirtualFields();
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

  @SuppressWarnings("unused")
  private static void testVirtualFields() {
    Runnable target = () -> {};
    setVirtualFieldValue(target, 42);
    Integer fieldValue = getVirtualFieldValue(target);
    if (fieldValue == null || fieldValue != 42) {
      System.out.println("virtual field not supported");
    } else {
      System.out.println("virtual field supported");
    }
  }

  public static void setVirtualFieldValue(Runnable target, Integer value) {
    // implementation should be provided by instrumentation
  }

  public static Integer getVirtualFieldValue(Runnable target) {
    // implementation should be provided by instrumentation
    return null;
  }
}
