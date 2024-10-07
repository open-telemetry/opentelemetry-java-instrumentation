/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.grizzly;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.bootstrap.internal.JavaagentHttpServerInstrumenters;
import io.opentelemetry.javaagent.bootstrap.servlet.AppServerBridge;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.HttpResponsePacket;

public final class GrizzlySingletons {

  private static final Instrumenter<HttpRequestPacket, HttpResponsePacket> INSTRUMENTER;

  static {
    INSTRUMENTER =
        JavaagentHttpServerInstrumenters.create(
            "io.opentelemetry.grizzly-2.3",
            new GrizzlyHttpAttributesGetter(),
            HttpRequestHeadersGetter.INSTANCE,
            builder ->
                builder
                    .addContextCustomizer(
                        (context, request, attributes) ->
                            new AppServerBridge.Builder()
                                .captureServletAttributes()
                                .recordException()
                                .init(context))
                    .addContextCustomizer(
                        (context, httpRequestPacket, startAttributes) ->
                            GrizzlyErrorHolder.init(context)));
  }

  public static Instrumenter<HttpRequestPacket, HttpResponsePacket> instrumenter() {
    return INSTRUMENTER;
  }

  private GrizzlySingletons() {}
}
