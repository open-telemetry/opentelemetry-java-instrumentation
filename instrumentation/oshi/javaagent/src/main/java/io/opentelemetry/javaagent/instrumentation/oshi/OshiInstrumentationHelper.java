/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.oshi;

import io.opentelemetry.instrumentation.oshi.SystemMetrics;

public final class OshiInstrumentationHelper {

  private OshiInstrumentationHelper() {}

  public static void registerObservers() {
    SystemMetrics.registerObservers();
  }
}
