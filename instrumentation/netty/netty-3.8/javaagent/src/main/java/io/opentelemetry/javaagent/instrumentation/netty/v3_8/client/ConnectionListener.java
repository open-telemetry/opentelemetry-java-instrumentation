/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v3_8.client;

import static io.opentelemetry.javaagent.instrumentation.netty.v3_8.client.NettyClientSingletons.connectInstrumenter;

import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.instrumentation.netty.common.NettyConnectRequest;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;

public final class ConnectionListener implements ChannelFutureListener {

  private final Context parentContext;
  private final NettyConnectRequest request;

  public ConnectionListener(Context parentContext, NettyConnectRequest request) {
    this.parentContext = parentContext;
    this.request = request;
  }

  @Override
  public void operationComplete(ChannelFuture future) {
    Throwable cause = future.getCause();
    if (cause != null && connectInstrumenter().shouldStart(parentContext, request)) {
      Context context = connectInstrumenter().start(parentContext, request);
      connectInstrumenter().end(context, request, future.getChannel(), cause);
    }
  }
}
