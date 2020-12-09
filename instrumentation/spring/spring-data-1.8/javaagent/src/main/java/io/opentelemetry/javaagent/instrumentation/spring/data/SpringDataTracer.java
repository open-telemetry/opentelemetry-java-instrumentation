/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.data;

import io.opentelemetry.instrumentation.api.instrumenter.BaseInstrumenter;

public final class SpringDataTracer extends BaseInstrumenter {
  private static final SpringDataTracer TRACER = new SpringDataTracer();

  public static SpringDataTracer tracer() {
    return TRACER;
  }

  private SpringDataTracer() {}

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.spring-data";
  }
}
