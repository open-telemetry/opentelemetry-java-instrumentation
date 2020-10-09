/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.servlet.filter;

import io.opentelemetry.instrumentation.api.tracer.BaseTracer;

public class FilterTracer extends BaseTracer {
  public static final FilterTracer TRACER = new FilterTracer();

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.auto.servlet";
  }
}
