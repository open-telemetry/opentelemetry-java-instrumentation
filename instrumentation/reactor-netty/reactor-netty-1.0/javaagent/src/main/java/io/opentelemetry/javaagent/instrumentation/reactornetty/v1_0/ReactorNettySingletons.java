/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactornetty.v1_0;

import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.javaagent.instrumentation.netty.common.client.NettyClientInstrumenterFactory;
import io.opentelemetry.javaagent.instrumentation.netty.common.client.NettyConnectionInstrumenter;

public final class ReactorNettySingletons {

  private static final boolean alwaysCreateConnectSpan =
      Config.get()
          .getBoolean("otel.instrumentation.reactor-netty.always-create-connect-span", false);

  private static final NettyConnectionInstrumenter CONNECTION_INSTRUMENTER;

  static {
    NettyClientInstrumenterFactory instrumenterFactory =
        new NettyClientInstrumenterFactory(
            "io.opentelemetry.reactor-netty-1.0", alwaysCreateConnectSpan, false);
    CONNECTION_INSTRUMENTER = instrumenterFactory.createConnectionInstrumenter();
  }

  public static NettyConnectionInstrumenter connectionInstrumenter() {
    return CONNECTION_INSTRUMENTER;
  }

  private ReactorNettySingletons() {}
}
