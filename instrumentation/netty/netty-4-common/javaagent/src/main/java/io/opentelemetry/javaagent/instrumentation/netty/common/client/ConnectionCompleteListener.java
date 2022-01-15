/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.common.client;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.instrumentation.netty.common.NettyConnectionRequest;

public class ConnectionCompleteListener implements GenericFutureListener<Future<Void>> {
  private final NettyConnectionInstrumenter instrumenter;
  private final Context context;
  private final NettyConnectionRequest request;

  public ConnectionCompleteListener(
      NettyConnectionInstrumenter instrumenter, Context context, NettyConnectionRequest request) {
    this.instrumenter = instrumenter;
    this.context = context;
    this.request = request;
  }

  @Override
  public void operationComplete(Future<Void> future) {
    Channel channel = null;
    if (future instanceof ChannelFuture) {
      channel = ((ChannelFuture) future).channel();
    }
    instrumenter.end(context, request, channel, future.cause());
  }
}
