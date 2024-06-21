/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v3_8.server;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.netty.common.internal.NettyErrorHolder;
import io.opentelemetry.javaagent.bootstrap.internal.JavaagentHttpServerInstrumenterBuilder;
import io.opentelemetry.javaagent.instrumentation.netty.v3_8.HttpRequestAndChannel;
import java.util.Optional;
import org.jboss.netty.handler.codec.http.HttpResponse;

final class NettyServerSingletons {

  private static final Instrumenter<HttpRequestAndChannel, HttpResponse> INSTRUMENTER;

  static {
    INSTRUMENTER =
        JavaagentHttpServerInstrumenterBuilder.createWithCustomizer(
            "io.opentelemetry.netty-3.8",
            new NettyHttpServerAttributesGetter(),
            Optional.of(NettyHeadersGetter.INSTANCE),
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
