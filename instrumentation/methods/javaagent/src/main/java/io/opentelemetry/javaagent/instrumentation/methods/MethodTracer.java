/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.methods;

import io.opentelemetry.instrumentation.api.instrumenter.BaseInstrumenter;

public class MethodTracer extends BaseInstrumenter {
  private static final MethodTracer TRACER = new MethodTracer();

  public static MethodTracer tracer() {
    return TRACER;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.external-annotations";
  }
}
