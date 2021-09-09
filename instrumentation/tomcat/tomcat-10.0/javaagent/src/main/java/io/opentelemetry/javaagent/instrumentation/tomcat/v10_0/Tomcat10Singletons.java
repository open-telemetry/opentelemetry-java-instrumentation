/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.tomcat.v10_0;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.servlet.jakarta.v5_0.JakartaServletAccessor;
import io.opentelemetry.javaagent.instrumentation.tomcat.common.TomcatInstrumenterBuilder;
import org.apache.coyote.Request;
import org.apache.coyote.Response;

public final class Tomcat10Singletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.tomcat-10.0";
  private static final Instrumenter<Request, Response> INSTRUMENTER =
      TomcatInstrumenterBuilder.newInstrumenter(
          INSTRUMENTATION_NAME,
          JakartaServletAccessor.INSTANCE,
          Tomcat10ServletEntityProvider.INSTANCE);

  public static Instrumenter<Request, Response> instrumenter() {
    return INSTRUMENTER;
  }

  private Tomcat10Singletons() {}
}
