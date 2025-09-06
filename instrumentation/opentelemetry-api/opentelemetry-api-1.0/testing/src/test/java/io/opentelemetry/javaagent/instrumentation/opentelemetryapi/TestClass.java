/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi;

import io.opentelemetry.context.Context;

class TestClass {
  static boolean shouldSuppressInstrumentation(Context context) {
    // this method is instrumented to call
    // InstrumentationUtil.shouldSuppressInstrumentation(context) to simulate agent code calling
    // that method
    return false;
  }

  private TestClass() {}
}
