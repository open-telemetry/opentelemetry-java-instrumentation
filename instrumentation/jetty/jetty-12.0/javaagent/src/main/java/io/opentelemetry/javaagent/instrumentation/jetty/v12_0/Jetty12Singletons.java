/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jetty.v12_0;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.bootstrap.internal.JavaagentHttpServerInstrumenters;
import io.opentelemetry.javaagent.bootstrap.servlet.AppServerBridge;
import io.opentelemetry.javaagent.instrumentation.servlet.common.ServletConfig;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;

class Jetty12Singletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.jetty-12.0";

  private static final Instrumenter<Request, Response> instrumenter;

  static {
    instrumenter =
        JavaagentHttpServerInstrumenters.create(
            INSTRUMENTATION_NAME,
            new Jetty12HttpAttributesGetter(),
            new Jetty12TextMapGetter(),
            builder -> {
              builder.addContextCustomizer(
                  (context, request, attributes) ->
                      new AppServerBridge.Builder()
                          .captureServletAttributes()
                          .recordException()
                          .init(context));
              if (ServletConfig.get().getCaptureRequestBodyEnabled()) {
                // with jetty 12, the request body can be read after the servlet service has
                // returned.
                builder.addAttributesExtractor(new Jetty12RequestBodyExtractor());
              }
            });
  }

  private static final Jetty12Helper helper = new Jetty12Helper(instrumenter);

  static Jetty12Helper helper() {
    return helper;
  }

  private Jetty12Singletons() {}
}
