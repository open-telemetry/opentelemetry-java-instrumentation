/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.jakarta.v5_0;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.servlet.ServletInstrumenterBuilder;
import io.opentelemetry.instrumentation.servlet.ServletRequestContext;
import io.opentelemetry.instrumentation.servlet.ServletResponseContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class Servlet5Singletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.servlet-5.0";

  private static final Instrumenter<
          ServletRequestContext<HttpServletRequest>, ServletResponseContext<HttpServletResponse>>
      INSTRUMENTER =
          ServletInstrumenterBuilder.newInstrumenter(
              INSTRUMENTATION_NAME, JakartaServletAccessor.INSTANCE, Servlet5RequestGetter.GETTER);

  public static Instrumenter<
          ServletRequestContext<HttpServletRequest>, ServletResponseContext<HttpServletResponse>>
      instrumenter() {
    return INSTRUMENTER;
  }

  private Servlet5Singletons() {}
}
