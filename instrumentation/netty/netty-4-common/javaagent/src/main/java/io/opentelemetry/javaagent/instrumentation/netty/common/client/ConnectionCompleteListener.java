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
  private final Context context;
  private final Context parentContext;

  public ConnectionCompleteListener(Context context, Context parentContext) {
    this.context = context;
    this.parentContext = parentContext;
  }

  @Override
  public void operationComplete(Future<Void> future) {
    AbstractNettyHttpClientTracer tracer = NettyHttpClientTracerAccess.getTracer();
    if (tracer == null) {
      return;
    }

    Channel channel = null;
    if (future instanceof ChannelFuture) {
      channel = ((ChannelFuture) future).channel();
    }
    tracer.endConnectionSpan(context, parentContext, null, channel, future.cause());
  }
}
