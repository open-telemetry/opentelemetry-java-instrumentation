/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_1;

import io.lettuce.core.tracing.Tracing;

public final class CompatibilityChecker {

  private CompatibilityChecker() {}

  private static final boolean isCompatible = isCompatible();

  private static boolean isCompatible() {
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
  public static boolean checkCompatible() {
    return isCompatible;
  }
}
