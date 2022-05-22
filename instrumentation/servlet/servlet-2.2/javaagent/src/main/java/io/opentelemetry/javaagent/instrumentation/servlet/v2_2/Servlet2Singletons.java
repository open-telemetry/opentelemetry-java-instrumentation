/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v2_2;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.util.ClassAndMethod;
import io.opentelemetry.javaagent.instrumentation.servlet.ServletInstrumenterBuilder;
import io.opentelemetry.javaagent.instrumentation.servlet.ServletRequestContext;
import io.opentelemetry.javaagent.instrumentation.servlet.ServletResponseContext;
import io.opentelemetry.javaagent.instrumentation.servlet.common.response.ResponseInstrumenterFactory;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public final class Servlet2Singletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.servlet-2.2";

  private static final Servlet2Helper HELPER;
  private static final Instrumenter<ClassAndMethod, Void> RESPONSE_INSTRUMENTER;

  static {
    Servlet2HttpAttributesGetter httpAttributesGetter =
        new Servlet2HttpAttributesGetter(Servlet2Accessor.INSTANCE);
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
                    httpAttributesGetter);

    HELPER = new Servlet2Helper(instrumenter);
    RESPONSE_INSTRUMENTER = ResponseInstrumenterFactory.createInstrumenter(INSTRUMENTATION_NAME);
  }

  public static Servlet2Helper helper() {
    return HELPER;
  }

  public static Instrumenter<ClassAndMethod, Void> responseInstrumenter() {
    return RESPONSE_INSTRUMENTER;
  }

  private Servlet2Singletons() {}
}
