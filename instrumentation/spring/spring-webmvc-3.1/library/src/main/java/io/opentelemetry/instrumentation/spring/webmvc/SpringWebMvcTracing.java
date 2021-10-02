/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webmvc;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import javax.servlet.Filter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Entrypoint for tracing Spring Web MVC apps. */
public final class SpringWebMvcTracing {

  /** Returns a new {@link SpringWebMvcTracing} configured with the given {@link OpenTelemetry}. */
  public static SpringWebMvcTracing create(OpenTelemetry openTelemetry) {
    return newBuilder(openTelemetry).build();
  }

  /**
   * Returns a new {@link SpringWebMvcTracingBuilder} configured with the given {@link
   * OpenTelemetry}.
   */
  public static SpringWebMvcTracingBuilder newBuilder(OpenTelemetry openTelemetry) {
    return new SpringWebMvcTracingBuilder(openTelemetry);
  }

  private final Instrumenter<HttpServletRequest, HttpServletResponse> instrumenter;

  SpringWebMvcTracing(Instrumenter<HttpServletRequest, HttpServletResponse> instrumenter) {
    this.instrumenter = instrumenter;
  }

  /** Returns a new {@link Filter} that generates telemetry for received HTTP requests. */
  public Filter newServletFilter() {
    return new WebMvcTracingFilter(instrumenter);
  }
}
