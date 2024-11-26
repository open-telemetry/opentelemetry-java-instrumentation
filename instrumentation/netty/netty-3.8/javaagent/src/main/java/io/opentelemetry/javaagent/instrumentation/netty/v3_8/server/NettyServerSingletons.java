/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v3_8.server;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.netty.common.internal.NettyErrorHolder;
import io.opentelemetry.javaagent.bootstrap.internal.JavaagentHttpServerInstrumenters;
import io.opentelemetry.javaagent.instrumentation.netty.v3_8.HttpRequestAndChannel;
import org.jboss.netty.handler.codec.http.HttpResponse;

final class NettyServerSingletons {

  private static final Instrumenter<HttpRequestAndChannel, HttpResponse> INSTRUMENTER;

  static {
    INSTRUMENTER =
        JavaagentHttpServerInstrumenters.create(
            "io.opentelemetry.netty-3.8",
            new NettyHttpServerAttributesGetter(),
            NettyHeadersGetter.INSTANCE,
            builder ->
                builder.addContextCustomizer(
                    (context, requestAndChannel, startAttributes) ->
                        NettyErrorHolder.init(context)));
  }

  public static Instrumenter<HttpRequestAndChannel, HttpResponse> instrumenter() {
    return INSTRUMENTER;
  }

  private NettyServerSingletons() {}
}
