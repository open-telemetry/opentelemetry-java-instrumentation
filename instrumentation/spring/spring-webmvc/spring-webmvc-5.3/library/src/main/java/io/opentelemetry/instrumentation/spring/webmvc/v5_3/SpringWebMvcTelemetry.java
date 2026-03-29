/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webmvc.v5_3;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import javax.servlet.Filter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Entrypoint for instrumenting Spring Web MVC apps. */
public final class SpringWebMvcTelemetry {

  /** Returns a new instance configured with the given {@link OpenTelemetry} instance. */
  public static SpringWebMvcTelemetry create(OpenTelemetry openTelemetry) {
    return builder(openTelemetry).build();
  }

  /** Returns a builder configured with the given {@link OpenTelemetry} instance. */
  public static SpringWebMvcTelemetryBuilder builder(OpenTelemetry openTelemetry) {
    return new SpringWebMvcTelemetryBuilder(openTelemetry);
  }

  private final Instrumenter<HttpServletRequest, HttpServletResponse> instrumenter;

  SpringWebMvcTelemetry(Instrumenter<HttpServletRequest, HttpServletResponse> instrumenter) {
    this.instrumenter = instrumenter;
  }

  /** Returns a {@link Filter} that instruments HTTP requests. */
  public Filter createServletFilter() {
    return new WebMvcTelemetryProducingFilter(instrumenter);
  }
}
