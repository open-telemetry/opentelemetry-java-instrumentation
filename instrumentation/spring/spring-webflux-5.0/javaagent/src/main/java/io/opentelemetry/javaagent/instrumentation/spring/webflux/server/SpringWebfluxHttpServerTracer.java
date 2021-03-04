/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.webflux.server;

import io.opentelemetry.instrumentation.api.tracer.BaseTracer;

public class SpringWebfluxHttpServerTracer extends BaseTracer {
  private static final SpringWebfluxHttpServerTracer TRACER = new SpringWebfluxHttpServerTracer();

  public static SpringWebfluxHttpServerTracer tracer() {
    return TRACER;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.spring-webflux-5.0";
  }
}
