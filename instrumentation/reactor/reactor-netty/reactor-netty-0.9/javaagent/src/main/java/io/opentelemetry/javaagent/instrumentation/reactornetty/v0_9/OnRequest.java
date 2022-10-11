/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactornetty.v0_9;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.netty.v4_1.NettyClientTelemetry;
import java.util.function.BiConsumer;
import reactor.netty.Connection;
import reactor.netty.http.client.HttpClientRequest;

public class OnRequest implements BiConsumer<HttpClientRequest, Connection> {
  @Override
  public void accept(HttpClientRequest r, Connection c) {
    Context context = r.currentContext().get(MapConnect.CONTEXT_ATTRIBUTE);
    NettyClientTelemetry.setChannelContext(c.channel(), context);
  }
}
