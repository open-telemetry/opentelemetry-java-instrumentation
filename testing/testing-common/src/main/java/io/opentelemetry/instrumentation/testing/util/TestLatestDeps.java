/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.util;

public final class TestLatestDeps {

  private static final boolean TEST_LATEST_DEPS = Boolean.getBoolean("testLatestDeps");

  /**
   * Returns {@code true} when the test is being run with {@code -PtestLatestDeps=true}, i.e.
   * against the latest published versions of the instrumented library instead of the pinned
   * earliest-supported versions.
   */
  public static boolean testLatestDeps() {
    return TEST_LATEST_DEPS;
  }

  private TestLatestDeps() {}
}
