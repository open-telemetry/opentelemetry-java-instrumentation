/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jetty.v8_0;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.servlet.ServletInstrumenterBuilder;
import io.opentelemetry.instrumentation.servlet.ServletRequestContext;
import io.opentelemetry.instrumentation.servlet.ServletResponseContext;
import io.opentelemetry.instrumentation.servlet.v3_0.Servlet3Accessor;
import io.opentelemetry.instrumentation.servlet.v3_0.Servlet3RequestGetter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class Jetty8Singletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.jetty-8.0";

  private static final Instrumenter<
          ServletRequestContext<HttpServletRequest>, ServletResponseContext<HttpServletResponse>>
      INSTRUMENTER =
          ServletInstrumenterBuilder.newInstrumenter(
              INSTRUMENTATION_NAME, Servlet3Accessor.INSTANCE, Servlet3RequestGetter.GETTER);

  public static Instrumenter<
          ServletRequestContext<HttpServletRequest>, ServletResponseContext<HttpServletResponse>>
      instrumenter() {
    return INSTRUMENTER;
  }

  private Jetty8Singletons() {}
}
