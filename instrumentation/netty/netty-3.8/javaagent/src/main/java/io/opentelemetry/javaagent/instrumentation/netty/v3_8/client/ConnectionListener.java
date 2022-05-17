/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v3_8.client;

import static io.opentelemetry.javaagent.instrumentation.netty.v3_8.client.NettyClientSingletons.connectionInstrumenter;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.internal.InstrumenterUtil;
import io.opentelemetry.javaagent.instrumentation.netty.common.NettyConnectionRequest;
import io.opentelemetry.javaagent.instrumentation.netty.common.Timer;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;

public final class ConnectionListener implements ChannelFutureListener {

  private final Context parentContext;
  private final NettyConnectionRequest request;
  private final Timer timer;

  public ConnectionListener(Context parentContext, NettyConnectionRequest request, Timer timer) {
    this.parentContext = parentContext;
    this.request = request;
    this.timer = timer;
  }

  @Override
  public void operationComplete(ChannelFuture future) {
    Throwable cause = future.getCause();
    if (cause != null && connectionInstrumenter().shouldStart(parentContext, request)) {
      InstrumenterUtil.startAndEnd(
          connectionInstrumenter(),
          parentContext,
          request,
          future.getChannel(),
          cause,
          timer.startTimeNanos(),
          timer.nowNanos());
    }
  }
}
