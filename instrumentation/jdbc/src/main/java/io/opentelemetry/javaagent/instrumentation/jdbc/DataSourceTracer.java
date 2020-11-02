/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jdbc;

import io.opentelemetry.instrumentation.api.tracer.BaseTracer;

public class DataSourceTracer extends BaseTracer {
  private static final DataSourceTracer TRACER = new DataSourceTracer();

  public static DataSourceTracer tracer() {
    return TRACER;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.auto.jdbc";
  }
}
