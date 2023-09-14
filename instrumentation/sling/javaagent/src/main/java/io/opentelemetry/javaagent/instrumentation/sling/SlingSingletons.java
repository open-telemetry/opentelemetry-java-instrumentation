/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.sling;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import org.apache.sling.api.SlingHttpServletRequest;

public final class SlingSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.sling-1.0";
  private static final String SPAN_NAME = "sling.request";

  static final String REQUEST_ATTR_RESOLVED_SERVLET_NAME = INSTRUMENTATION_NAME + ".resovledServletName";
  private static final Instrumenter<SlingHttpServletRequest, Void>
      INSTRUMENTER = Instrumenter.
      <SlingHttpServletRequest, Void> builder(GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, s -> SPAN_NAME)
          .buildInstrumenter();

         /* ServletInstrumenterBuilder.<HttpServletRequest, HttpServletResponse>create()
              .addContextCustomizer(
                  (context, request, attributes) -> new AppServerBridge.Builder().init(context))
              .build(INSTRUMENTATION_NAME, Servlet3Accessor.INSTANCE);*/

  public static Instrumenter<SlingHttpServletRequest, Void> helper() {
    return INSTRUMENTER;
  }

  private SlingSingletons() {}
}
