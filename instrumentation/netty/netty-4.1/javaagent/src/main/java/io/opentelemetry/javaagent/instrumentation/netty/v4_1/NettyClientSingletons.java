/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_1;

import static io.opentelemetry.instrumentation.netty.common.v4_0.internal.client.NettyConnectionInstrumentationFlag.enabledOrErrorOnly;

import io.netty.handler.codec.http.HttpResponse;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.builder.internal.DefaultHttpClientInstrumenterBuilder;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.api.incubator.semconv.net.PeerServiceResolver;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.netty.common.v4_0.NettyRequest;
import io.opentelemetry.instrumentation.netty.common.v4_0.internal.client.NettyClientInstrumenterBuilderFactory;
import io.opentelemetry.instrumentation.netty.common.v4_0.internal.client.NettyClientInstrumenterFactory;
import io.opentelemetry.instrumentation.netty.common.v4_0.internal.client.NettyConnectionInstrumenter;
import io.opentelemetry.instrumentation.netty.common.v4_0.internal.client.NettySslInstrumenter;
import io.opentelemetry.instrumentation.netty.v4_1.internal.client.NettyClientHandlerFactory;

public final class NettyClientSingletons {

  private static final boolean connectionTelemetryEnabled =
      DeclarativeConfigUtil.getBoolean(
              GlobalOpenTelemetry.get(), "java", "netty", "connection_telemetry", "enabled")
          .orElse(false);
  private static final boolean sslTelemetryEnabled =
      DeclarativeConfigUtil.getBoolean(
              GlobalOpenTelemetry.get(), "java", "netty", "ssl_telemetry", "enabled")
          .orElse(false);

  private static final Instrumenter<NettyRequest, HttpResponse> INSTRUMENTER;
  private static final NettyConnectionInstrumenter CONNECTION_INSTRUMENTER;
  private static final NettySslInstrumenter SSL_INSTRUMENTER;
  private static final NettyClientHandlerFactory CLIENT_HANDLER_FACTORY;

  static {
    DefaultHttpClientInstrumenterBuilder<NettyRequest, HttpResponse> builder =
        NettyClientInstrumenterBuilderFactory.create(
                "io.opentelemetry.netty-4.1", GlobalOpenTelemetry.get())
            .configure(GlobalOpenTelemetry.get());
    NettyClientInstrumenterFactory factory =
        new NettyClientInstrumenterFactory(
            builder,
            enabledOrErrorOnly(connectionTelemetryEnabled),
            enabledOrErrorOnly(sslTelemetryEnabled));
    INSTRUMENTER = factory.instrumenter();
    CONNECTION_INSTRUMENTER =
        factory.createConnectionInstrumenter(PeerServiceResolver.create(GlobalOpenTelemetry.get()));
    SSL_INSTRUMENTER = factory.createSslInstrumenter();
    CLIENT_HANDLER_FACTORY =
        new NettyClientHandlerFactory(
            INSTRUMENTER,
            DeclarativeConfigUtil.getBoolean(
                    GlobalOpenTelemetry.get(),
                    "java",
                    "http",
                    "client",
                    "emit_experimental_telemetry")
                .orElse(false));
  }

  public static Instrumenter<NettyRequest, HttpResponse> instrumenter() {
    return INSTRUMENTER;
  }

  public static NettyConnectionInstrumenter connectionInstrumenter() {
    return CONNECTION_INSTRUMENTER;
  }

  public static NettySslInstrumenter sslInstrumenter() {
    return SSL_INSTRUMENTER;
  }

  public static NettyClientHandlerFactory clientHandlerFactory() {
    return CLIENT_HANDLER_FACTORY;
  }

  private NettyClientSingletons() {}
}
