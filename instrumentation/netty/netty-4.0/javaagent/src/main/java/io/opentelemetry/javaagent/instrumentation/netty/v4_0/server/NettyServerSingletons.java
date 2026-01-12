/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_0.server;

import io.netty.handler.codec.http.HttpResponse;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.netty.common.internal.NettyErrorHolder;
import io.opentelemetry.instrumentation.netty.common.v4_0.NettyRequest;
import io.opentelemetry.instrumentation.netty.common.v4_0.internal.server.HttpRequestHeadersGetter;
import io.opentelemetry.instrumentation.netty.common.v4_0.internal.server.NettyHttpServerAttributesGetter;
import io.opentelemetry.javaagent.bootstrap.internal.JavaagentHttpServerInstrumenters;

public final class NettyServerSingletons {

  private static final Instrumenter<NettyRequest, HttpResponse> INSTRUMENTER =
      JavaagentHttpServerInstrumenters.create(
          "io.opentelemetry.netty-4.0",
          new NettyHttpServerAttributesGetter(),
          HttpRequestHeadersGetter.INSTANCE,
          builder ->
              builder.addContextCustomizer(
                  (context, requestAndChannel, startAttributes) -> NettyErrorHolder.init(context)));

  public static Instrumenter<NettyRequest, HttpResponse> instrumenter() {
    return INSTRUMENTER;
  }

  private NettyServerSingletons() {}
}
