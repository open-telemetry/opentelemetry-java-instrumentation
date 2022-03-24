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

/** Entrypoint for instrumenting Spring Web MVC apps. */
public final class SpringWebMvcTelemetry {

  /**
   * Returns a new {@link SpringWebMvcTelemetry} configured with the given {@link OpenTelemetry}.
   */
  public static SpringWebMvcTelemetry create(OpenTelemetry openTelemetry) {
    return builder(openTelemetry).build();
  }

  /**
   * Returns a new {@link SpringWebMvcTelemetryBuilder} configured with the given {@link
   * OpenTelemetry}.
   */
  public static SpringWebMvcTelemetryBuilder builder(OpenTelemetry openTelemetry) {
    return new SpringWebMvcTelemetryBuilder(openTelemetry);
  }

  private final Instrumenter<HttpServletRequest, HttpServletResponse> instrumenter;

  SpringWebMvcTelemetry(Instrumenter<HttpServletRequest, HttpServletResponse> instrumenter) {
    this.instrumenter = instrumenter;
  }

  /** Returns a new {@link Filter} that generates telemetry for received HTTP requests. */
  public Filter newServletFilter() {
    return new WebMvcTracingFilter(instrumenter);
  }
}
