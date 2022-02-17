/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap;

import java.util.function.Function;

/** Helper class for detecting whether given class is an injected helper class. */
public final class InjectedHelperClassDetector {

  private InjectedHelperClassDetector() {}

  private static volatile Function<Class<?>, Boolean> helperClassDetector;

  /** Sets the {@link Function} for detecting injected helper classes. */
  public static void internalSetHelperClassDetector(
      Function<Class<?>, Boolean> helperClassDetector) {
    if (InjectedHelperClassDetector.helperClassDetector != null) {
      // Only possible by misuse of this API, just ignore.
      return;
    }
    InjectedHelperClassDetector.helperClassDetector = helperClassDetector;
  }

  public static boolean isHelperClass(Class<?> clazz) {
    if (helperClassDetector == null) {
      return false;
    }
    return helperClassDetector.apply(clazz);
  }
}
