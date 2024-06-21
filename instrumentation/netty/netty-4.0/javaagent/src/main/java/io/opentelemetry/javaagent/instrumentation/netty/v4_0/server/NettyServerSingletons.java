/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_0.server;

import io.netty.handler.codec.http.HttpResponse;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.netty.common.internal.NettyErrorHolder;
import io.opentelemetry.instrumentation.netty.v4.common.HttpRequestAndChannel;
import io.opentelemetry.instrumentation.netty.v4.common.internal.server.HttpRequestHeadersGetter;
import io.opentelemetry.instrumentation.netty.v4.common.internal.server.NettyHttpServerAttributesGetter;
import io.opentelemetry.javaagent.bootstrap.internal.JavaagentHttpServerInstrumenterBuilder;
import java.util.Optional;

public final class NettyServerSingletons {

  private static final Instrumenter<HttpRequestAndChannel, HttpResponse> INSTRUMENTER =
      JavaagentHttpServerInstrumenterBuilder.createWithCustomizer(
          "io.opentelemetry.netty-4.0",
          new NettyHttpServerAttributesGetter(),
          Optional.of(
              HttpRequestHeadersGetter.INSTANCE),
          builder -> builder.addContextCustomizer(
              (context, requestAndChannel, startAttributes) -> NettyErrorHolder.init(context)));


  public static Instrumenter<HttpRequestAndChannel, HttpResponse> instrumenter() {
    return INSTRUMENTER;
  }

  private NettyServerSingletons() {}
}
