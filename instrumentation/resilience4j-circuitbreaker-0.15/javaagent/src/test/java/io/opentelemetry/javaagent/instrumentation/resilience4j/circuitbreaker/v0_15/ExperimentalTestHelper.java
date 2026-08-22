/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.resilience4j.circuitbreaker.v0_15;

final class ExperimentalTestHelper {

  private static final boolean EXPERIMENTAL =
      Boolean.getBoolean(
          "otel.instrumentation.resilience4j-circuitbreaker.experimental-span-attributes");

  static <T> T experimental(T value) {
    return EXPERIMENTAL ? value : null;
  }

  private ExperimentalTestHelper() {}
}
