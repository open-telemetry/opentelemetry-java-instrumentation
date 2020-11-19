/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.filter;

import io.opentelemetry.instrumentation.api.tracer.BaseTracer;

public class FilterTracer extends BaseTracer {
  private static final FilterTracer TRACER = new FilterTracer();

  public static FilterTracer tracer() {
    return TRACER;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.servlet";
  }
}
