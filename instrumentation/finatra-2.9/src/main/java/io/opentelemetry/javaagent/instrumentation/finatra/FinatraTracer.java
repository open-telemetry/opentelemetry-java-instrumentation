/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.finatra;

import io.opentelemetry.instrumentation.api.tracer.BaseTracer;

public class FinatraTracer extends BaseTracer {
  public static final FinatraTracer TRACER = new FinatraTracer();

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.auto.finatra-2.9";
  }
}
