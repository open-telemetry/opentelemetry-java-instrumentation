/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.webflux.server;

import io.opentelemetry.instrumentation.api.instrumenter.BaseInstrumenter;

public class SpringWebfluxHttpServerTracer extends BaseInstrumenter {
  private static final SpringWebfluxHttpServerTracer TRACER = new SpringWebfluxHttpServerTracer();

  public static SpringWebfluxHttpServerTracer tracer() {
    return TRACER;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.spring-webflux";
  }
}
