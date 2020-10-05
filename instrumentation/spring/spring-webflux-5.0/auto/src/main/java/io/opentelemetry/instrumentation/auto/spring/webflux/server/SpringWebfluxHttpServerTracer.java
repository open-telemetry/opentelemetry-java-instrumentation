/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.spring.webflux.server;

import io.opentelemetry.instrumentation.api.tracer.BaseTracer;

public class SpringWebfluxHttpServerTracer extends BaseTracer {
  public static final SpringWebfluxHttpServerTracer TRACER = new SpringWebfluxHttpServerTracer();

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.auto.spring-webflux-5.0";
  }
}
