/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.oshi;

import io.opentelemetry.javaagent.tooling.Instrumenter;

public abstract class AbstractOshiInstrumentation extends Instrumenter.Default {

  public AbstractOshiInstrumentation() {
    super("oshi");
  }

  @Override
  public final String[] helperClassNames() {
    return new String[] {
      packageName + ".OshiInstrumentationHelper",
      "io.opentelemetry.instrumentation.oshi.SystemMetrics",
      "io.opentelemetry.instrumentation.oshi.SystemMetrics$1",
      "io.opentelemetry.instrumentation.oshi.SystemMetrics$2",
      "io.opentelemetry.instrumentation.oshi.SystemMetrics$3",
      "io.opentelemetry.instrumentation.oshi.SystemMetrics$4",
      "io.opentelemetry.instrumentation.oshi.SystemMetrics$5",
      "io.opentelemetry.instrumentation.oshi.SystemMetrics$6",
      "io.opentelemetry.instrumentation.oshi.SystemMetrics$7"
    };
  }
}
