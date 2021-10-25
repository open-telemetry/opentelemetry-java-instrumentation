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

public class ConnectionCompleteListener implements GenericFutureListener<Future<Void>> {
  private final NettyConnectInstrumenter instrumenter;
  private final Context context;
  private final NettyConnectRequest request;

  public ConnectionCompleteListener(
      NettyConnectInstrumenter instrumenter, Context context, NettyConnectRequest request) {
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
