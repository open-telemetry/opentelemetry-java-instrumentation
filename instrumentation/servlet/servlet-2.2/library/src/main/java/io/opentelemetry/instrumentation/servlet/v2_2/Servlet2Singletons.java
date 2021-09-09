/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.v2_2;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpAttributesExtractor;
import io.opentelemetry.instrumentation.servlet.ServletInstrumenterBuilder;
import io.opentelemetry.instrumentation.servlet.ServletRequestContext;
import io.opentelemetry.instrumentation.servlet.ServletResponseContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class Servlet2Singletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.servlet-2.2";

  private static final Instrumenter<
          ServletRequestContext<HttpServletRequest>, ServletResponseContext<HttpServletResponse>>
      INSTRUMENTER;

  static {
    HttpAttributesExtractor<
            ServletRequestContext<HttpServletRequest>, ServletResponseContext<HttpServletResponse>>
        httpAttributesExtractor = new Servlet2HttpAttributesExtractor(Servlet2Accessor.INSTANCE);
    SpanNameExtractor<ServletRequestContext<HttpServletRequest>> spanNameExtractor =
        new Servlet2SpanNameExtractor<>(Servlet2Accessor.INSTANCE);

    INSTRUMENTER =
        ServletInstrumenterBuilder.newBuilder(
                INSTRUMENTATION_NAME,
                Servlet2Accessor.INSTANCE,
                spanNameExtractor,
                httpAttributesExtractor)
            .newServerInstrumenter(Servlet2RequestGetter.GETTER);
  }

  public static Instrumenter<
          ServletRequestContext<HttpServletRequest>, ServletResponseContext<HttpServletResponse>>
      instrumenter() {
    return INSTRUMENTER;
  }

  private Servlet2Singletons() {}
}
