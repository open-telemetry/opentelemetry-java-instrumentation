/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jetty.v12_0;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.bootstrap.internal.JavaagentHttpServerInstrumenters;
import io.opentelemetry.javaagent.bootstrap.servlet.AppServerBridge;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;

public final class Jetty12Singletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.jetty-12.0";

  private static final Instrumenter<Request, Response> INSTRUMENTER;

  static {
    INSTRUMENTER =
        JavaagentHttpServerInstrumenters.create(
            INSTRUMENTATION_NAME,
            new Jetty12HttpAttributesGetter(),
            Jetty12TextMapGetter.INSTANCE,
            builder ->
                builder.addContextCustomizer(
                    (context, request, attributes) ->
                        new AppServerBridge.Builder()
                            .captureServletAttributes()
                            .recordException()
                            .init(context)));
  }

  private static final Jetty12Helper HELPER = new Jetty12Helper(INSTRUMENTER);

  public static Jetty12Helper helper() {
    return HELPER;
  }

  private Jetty12Singletons() {}
}
