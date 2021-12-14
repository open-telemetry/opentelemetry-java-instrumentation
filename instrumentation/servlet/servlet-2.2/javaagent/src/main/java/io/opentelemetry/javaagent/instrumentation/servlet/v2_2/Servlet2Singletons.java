/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v2_2;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerAttributesExtractor;
import io.opentelemetry.javaagent.instrumentation.servlet.ServletInstrumenterBuilder;
import io.opentelemetry.javaagent.instrumentation.servlet.ServletRequestContext;
import io.opentelemetry.javaagent.instrumentation.servlet.ServletResponseContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public final class Servlet2Singletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.servlet-2.2";

  private static final Servlet2Helper HELPER;

  static {
    HttpServerAttributesExtractor<
            ServletRequestContext<HttpServletRequest>, ServletResponseContext<HttpServletResponse>>
        httpAttributesExtractor = new Servlet2HttpAttributesExtractor(Servlet2Accessor.INSTANCE);
    SpanNameExtractor<ServletRequestContext<HttpServletRequest>> spanNameExtractor =
        new Servlet2SpanNameExtractor<>(Servlet2Accessor.INSTANCE);

    Instrumenter<
            ServletRequestContext<HttpServletRequest>, ServletResponseContext<HttpServletResponse>>
        instrumenter =
            ServletInstrumenterBuilder.<HttpServletRequest, HttpServletResponse>create()
                .build(
                    INSTRUMENTATION_NAME,
                    Servlet2Accessor.INSTANCE,
                    spanNameExtractor,
                    httpAttributesExtractor);

    HELPER = new Servlet2Helper(instrumenter);
  }

  public static Servlet2Helper helper() {
    return HELPER;
  }

  private Servlet2Singletons() {}
}
