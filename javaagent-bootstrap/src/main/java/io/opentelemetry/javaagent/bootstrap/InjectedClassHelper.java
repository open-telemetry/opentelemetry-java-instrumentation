/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap;

import java.security.ProtectionDomain;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;

/** Helper class for detecting and loading injected helper classes. */
public final class InjectedClassHelper {

  private InjectedClassHelper() {}

  private static volatile BiPredicate<ClassLoader, String> helperClassDetector;

  /** Sets the {@link Function} for detecting injected helper classes. */
  public static void internalSetHelperClassDetector(
      BiPredicate<ClassLoader, String> helperClassDetector) {
    if (InjectedClassHelper.helperClassDetector != null) {
      // Only possible by misuse of this API, just ignore.
      return;
    }
    InjectedClassHelper.helperClassDetector = helperClassDetector;
  }

  public static boolean isHelperClass(Class<?> clazz) {
    return isHelperClass(clazz.getClassLoader(), clazz.getName());
  }

  public static boolean isHelperClass(ClassLoader classLoader, String className) {
    if (helperClassDetector == null) {
      return false;
    }
    return helperClassDetector.test(classLoader, className);
  }

  private static volatile BiFunction<ClassLoader, String, HelperClassInfo> helperClassInfo;

  public static void internalSetHelperClassInfo(
      BiFunction<ClassLoader, String, HelperClassInfo> helperClassInfo) {
    if (InjectedClassHelper.helperClassInfo != null) {
      // Only possible by misuse of this API, just ignore.
      return;
    }
    InjectedClassHelper.helperClassInfo = helperClassInfo;
  }

  public static HelperClassInfo getHelperClassInfo(ClassLoader classLoader, String className) {
    if (helperClassInfo == null) {
      return null;
    }
    return helperClassInfo.apply(classLoader, className);
  }

  public interface HelperClassInfo {
    byte[] getClassBytes();

    ProtectionDomain getProtectionDomain();
  }
}
