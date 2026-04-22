/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_1;

import io.lettuce.core.tracing.Tracing;

public class CompatibilityChecker {

  private static final boolean IS_COMPATIBLE = computeCompatibility();

  private static boolean computeCompatibility() {
    try {
      Tracing.getContext();
      return true;
    } catch (Throwable t) {
      return false;
    }
  }

  // related to https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/10997
  // if users are using incompatible versions of reactor-core and lettuce
  // then just disable the instrumentation
  public static boolean isCompatible() {
    return IS_COMPATIBLE;
  }

  private CompatibilityChecker() {}
}
