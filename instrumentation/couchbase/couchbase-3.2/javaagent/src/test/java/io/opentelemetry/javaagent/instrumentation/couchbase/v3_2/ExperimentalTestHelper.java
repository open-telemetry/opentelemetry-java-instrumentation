/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v3_2;

final class ExperimentalTestHelper {
  static final boolean EXPERIMENTAL_TELEMETRY =
      Boolean.getBoolean("otel.instrumentation.couchbase.emit-experimental-telemetry");

  static <T> T experimental(T value) {
    return EXPERIMENTAL_TELEMETRY ? value : null;
  }

  private ExperimentalTestHelper() {}
}
