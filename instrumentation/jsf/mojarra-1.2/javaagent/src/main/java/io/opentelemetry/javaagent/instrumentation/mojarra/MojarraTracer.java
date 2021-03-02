/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mojarra;

import io.opentelemetry.instrumentation.jsf.JsfTracer;

public class MojarraTracer extends JsfTracer {
  private static final MojarraTracer TRACER = new MojarraTracer();

  public static MojarraTracer tracer() {
    return TRACER;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.mojarra-1.2";
  }
}
