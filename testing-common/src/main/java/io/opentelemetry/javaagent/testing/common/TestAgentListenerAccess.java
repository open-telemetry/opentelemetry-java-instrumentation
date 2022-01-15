/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.testing.common;

import static java.lang.invoke.MethodType.methodType;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

public final class TestAgentListenerAccess {

  private static final MethodHandle reset;
  private static final MethodHandle getInstrumentationErrorCount;
  private static final MethodHandle getMuzzleFailureCount;
  private static final MethodHandle getIgnoredButTransformedClassNames;
  private static final MethodHandle addSkipTransformationCondition;
  private static final MethodHandle addSkipErrorCondition;

  static {
    try {
      MethodHandles.Lookup lookup = MethodHandles.lookup();
      Class<?> testAgentListenerClass =
          AgentClassLoaderAccess.loadClass(
              "io.opentelemetry.javaagent.testing.bytebuddy.TestAgentListener");
      reset = lookup.findStatic(testAgentListenerClass, "reset", methodType(void.class));
      getInstrumentationErrorCount =
          lookup.findStatic(
              testAgentListenerClass, "getInstrumentationErrorCount", methodType(int.class));
      getMuzzleFailureCount =
          lookup.findStatic(
              testAgentListenerClass, "getAndResetMuzzleFailureCount", methodType(int.class));
      getIgnoredButTransformedClassNames =
          lookup.findStatic(
              testAgentListenerClass, "getIgnoredButTransformedClassNames", methodType(List.class));
      addSkipTransformationCondition =
          lookup.findStatic(
              testAgentListenerClass,
              "addSkipTransformationCondition",
              methodType(void.class, Function.class));
      addSkipErrorCondition =
          lookup.findStatic(
              testAgentListenerClass,
              "addSkipErrorCondition",
              methodType(void.class, BiFunction.class));
    } catch (Throwable t) {
      throw new AssertionError("Could not initialize accessors for TestAgentListener.", t);
    }
  }

  public static void reset() {
    try {
      reset.invokeExact();
    } catch (Throwable t) {
      throw new AssertionError("Could not invoke TestAgentListener.reset", t);
    }
  }

  public static int getInstrumentationErrorCount() {
    try {
      return (int) getInstrumentationErrorCount.invokeExact();
    } catch (Throwable t) {
      throw new AssertionError(
          "Could not invoke TestAgentListener.getInstrumentationErrorCount", t);
    }
  }

  public static int getAndResetMuzzleFailureCount() {
    try {
      return (int) getMuzzleFailureCount.invokeExact();
    } catch (Throwable t) {
      throw new AssertionError("Could not invoke TestAgentListener.getMuzzleFailureCount", t);
    }
  }

  @SuppressWarnings("unchecked")
  public static List<String> getIgnoredButTransformedClassNames() {
    try {
      return (List<String>) getIgnoredButTransformedClassNames.invokeExact();
    } catch (Throwable t) {
      throw new AssertionError(
          "Could not invoke TestAgentListener.getIgnoredButTransformedClassNames", t);
    }
  }

  public static void addSkipTransformationCondition(Function<String, Boolean> condition) {
    try {
      addSkipTransformationCondition.invokeExact(condition);
    } catch (Throwable t) {
      throw new AssertionError(
          "Could not invoke TestAgentListener.addSkipTransformationCondition", t);
    }
  }

  public static void addSkipErrorCondition(BiFunction<String, Throwable, Boolean> condition) {
    try {
      addSkipErrorCondition.invokeExact(condition);
    } catch (Throwable t) {
      throw new AssertionError("Could not invoke TestAgentListener.addSkipErrorCondition", t);
    }
  }

  private TestAgentListenerAccess() {}
}
