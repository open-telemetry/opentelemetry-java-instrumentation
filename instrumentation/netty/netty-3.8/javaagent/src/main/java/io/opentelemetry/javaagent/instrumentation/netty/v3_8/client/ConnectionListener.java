/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v3_8.client;

import static io.opentelemetry.javaagent.instrumentation.netty.v3_8.client.NettyClientSingletons.connectionInstrumenter;

import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.instrumentation.netty.common.NettyConnectionRequest;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;

public final class ConnectionListener implements ChannelFutureListener {

  private final Context parentContext;
  private final NettyConnectionRequest request;

  public ConnectionListener(Context parentContext, NettyConnectionRequest request) {
    this.parentContext = parentContext;
    this.request = request;
  }

  @Override
  public void operationComplete(ChannelFuture future) {
    Throwable cause = future.getCause();
    if (cause != null && connectionInstrumenter().shouldStart(parentContext, request)) {
      Context context = connectionInstrumenter().start(parentContext, request);
      connectionInstrumenter().end(context, request, future.getChannel(), cause);
    }
  }
}
