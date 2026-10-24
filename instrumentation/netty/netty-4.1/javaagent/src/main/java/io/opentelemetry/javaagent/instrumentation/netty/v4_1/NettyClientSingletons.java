/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_1;

import static io.opentelemetry.instrumentation.netty.common.v4_0.internal.client.NettyConnectionInstrumentationFlag.enabledOrErrorOnly;

import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpResponse;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.builder.internal.DefaultHttpClientInstrumenterBuilder;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.instrumentation.netty.common.v4_0.internal.NettyCommonRequest;
import io.opentelemetry.instrumentation.netty.common.v4_0.internal.client.NettyClientInstrumenterBuilderFactory;
import io.opentelemetry.instrumentation.netty.common.v4_0.internal.client.NettyClientInstrumenterFactory;
import io.opentelemetry.instrumentation.netty.common.v4_0.internal.client.NettyConnectionInstrumenter;
import io.opentelemetry.instrumentation.netty.common.v4_0.internal.client.NettySslInstrumenter;
import io.opentelemetry.instrumentation.netty.v4_1.internal.client.ClientContexts;
import io.opentelemetry.instrumentation.netty.v4_1.internal.client.NettyClientHandlerFactory;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;

public class NettyClientSingletons {

  private static final boolean connectionTelemetryEnabled =
      DeclarativeConfigUtil.getInstrumentationConfig(GlobalOpenTelemetry.get(), "netty")
          .get("connection_telemetry")
          .getBoolean("enabled", false);
  private static final boolean sslTelemetryEnabled =
      DeclarativeConfigUtil.getInstrumentationConfig(GlobalOpenTelemetry.get(), "netty")
          .get("ssl_telemetry")
          .getBoolean("enabled", false);

  private static final Instrumenter<NettyCommonRequest, HttpResponse> instrumenter;
  private static final NettyConnectionInstrumenter connectionInstrumenter;
  private static final NettySslInstrumenter sslInstrumenter;
  private static final NettyClientHandlerFactory clientHandlerFactory;
  public static final VirtualField<HttpObject, ClientContexts.ResponseCallback> RESPONSE_CALLBACK =
      VirtualField.find(HttpObject.class, ClientContexts.ResponseCallback.class);
  public static final VirtualField<Runnable, ClientContexts.ResponseCallbackDispatch>
      RESPONSE_CALLBACK_TASK =
          VirtualField.find(Runnable.class, ClientContexts.ResponseCallbackDispatch.class);
  public static final VirtualField<Runnable, ClientContexts.FailureNotification>
      FAILURE_NOTIFICATION_TASK =
          VirtualField.find(Runnable.class, ClientContexts.FailureNotification.class);

  static {
    DefaultHttpClientInstrumenterBuilder<NettyCommonRequest, HttpResponse> builder =
        NettyClientInstrumenterBuilderFactory.create(
                "io.opentelemetry.netty-4.1", GlobalOpenTelemetry.get())
            .configure(AgentCommonConfig.get());
    NettyClientInstrumenterFactory factory =
        new NettyClientInstrumenterFactory(
            builder,
            enabledOrErrorOnly(connectionTelemetryEnabled),
            enabledOrErrorOnly(sslTelemetryEnabled));
    instrumenter = factory.instrumenter();
    connectionInstrumenter = factory.createConnectionInstrumenter(GlobalOpenTelemetry.get());
    sslInstrumenter = factory.createSslInstrumenter();
    clientHandlerFactory =
        new NettyClientHandlerFactory(
            instrumenter, AgentCommonConfig.get().shouldEmitExperimentalHttpClientTelemetry());
  }

  public static Instrumenter<NettyCommonRequest, HttpResponse> instrumenter() {
    return instrumenter;
  }

  public static NettyConnectionInstrumenter connectionInstrumenter() {
    return connectionInstrumenter;
  }

  public static NettySslInstrumenter sslInstrumenter() {
    return sslInstrumenter;
  }

  public static NettyClientHandlerFactory clientHandlerFactory() {
    return clientHandlerFactory;
  }

  private NettyClientSingletons() {}
}
