/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.v3_0;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.servlet.ServletInstrumenterBuilder;
import io.opentelemetry.instrumentation.servlet.ServletRequestContext;
import io.opentelemetry.instrumentation.servlet.ServletResponseContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public final class Servlet3Singletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.servlet-3.0";

  private static final Instrumenter<
          ServletRequestContext<HttpServletRequest>, ServletResponseContext<HttpServletResponse>>
      INSTRUMENTER =
          ServletInstrumenterBuilder.newInstrumenter(
              INSTRUMENTATION_NAME, Servlet3Accessor.INSTANCE);

  public static Instrumenter<
          ServletRequestContext<HttpServletRequest>, ServletResponseContext<HttpServletResponse>>
      instrumenter() {
    return INSTRUMENTER;
  }

  private Servlet3Singletons() {}
}
