/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_1;

import static io.opentelemetry.instrumentation.netty.v4.common.internal.client.NettyConnectionInstrumentationFlag.enabledOrErrorOnly;

import io.netty.handler.codec.http.HttpResponse;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.builder.internal.DefaultHttpClientInstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.netty.v4.common.HttpRequestAndChannel;
import io.opentelemetry.instrumentation.netty.v4.common.internal.client.NettyClientInstrumenterBuilder;
import io.opentelemetry.instrumentation.netty.v4.common.internal.client.NettyClientInstrumenterFactory;
import io.opentelemetry.instrumentation.netty.v4.common.internal.client.NettyConnectionInstrumenter;
import io.opentelemetry.instrumentation.netty.v4.common.internal.client.NettySslInstrumenter;
import io.opentelemetry.instrumentation.netty.v4_1.internal.client.NettyClientHandlerFactory;
import io.opentelemetry.javaagent.bootstrap.internal.CommonConfig;
import io.opentelemetry.javaagent.bootstrap.internal.InstrumentationConfig;

public final class NettyClientSingletons {

  private static final boolean connectionTelemetryEnabled =
      InstrumentationConfig.get()
          .getBoolean("otel.instrumentation.netty.connection-telemetry.enabled", false);
  private static final boolean sslTelemetryEnabled =
      InstrumentationConfig.get()
          .getBoolean("otel.instrumentation.netty.ssl-telemetry.enabled", false);

  private static final Instrumenter<HttpRequestAndChannel, HttpResponse> INSTRUMENTER;
  private static final NettyConnectionInstrumenter CONNECTION_INSTRUMENTER;
  private static final NettySslInstrumenter SSL_INSTRUMENTER;
  private static final NettyClientHandlerFactory CLIENT_HANDLER_FACTORY;

  static {
    NettyClientInstrumenterBuilder builder =
        new NettyClientInstrumenterBuilder("io.opentelemetry.netty-4.1", GlobalOpenTelemetry.get());
    DefaultHttpClientInstrumenterBuilder.unwrapAndConfigure(CommonConfig.get(), builder);
    NettyClientInstrumenterFactory factory =
        new NettyClientInstrumenterFactory(
            builder,
            enabledOrErrorOnly(connectionTelemetryEnabled),
            enabledOrErrorOnly(sslTelemetryEnabled));
    INSTRUMENTER = factory.instrumenter();
    CONNECTION_INSTRUMENTER = factory.createConnectionInstrumenter();
    SSL_INSTRUMENTER = factory.createSslInstrumenter();
    CLIENT_HANDLER_FACTORY =
        new NettyClientHandlerFactory(
            INSTRUMENTER, CommonConfig.get().shouldEmitExperimentalHttpClientTelemetry());
  }

  public static Instrumenter<HttpRequestAndChannel, HttpResponse> instrumenter() {
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
