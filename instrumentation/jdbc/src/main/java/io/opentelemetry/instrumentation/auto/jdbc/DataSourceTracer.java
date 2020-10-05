/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.jdbc;

import io.opentelemetry.instrumentation.api.tracer.BaseTracer;

public class DataSourceTracer extends BaseTracer {
  public static final DataSourceTracer TRACER = new DataSourceTracer();

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.auto.jdbc";
  }
}
