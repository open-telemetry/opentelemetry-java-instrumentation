/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.common.client;

import io.netty.channel.Channel;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.instrumentation.netty.common.NettyConnectRequest;
import javax.annotation.Nullable;

final class NettyConnectInstrumenterImpl implements NettyConnectInstrumenter {

  private final Instrumenter<NettyConnectRequest, Channel> instrumenter;

  NettyConnectInstrumenterImpl(Instrumenter<NettyConnectRequest, Channel> instrumenter) {
    this.instrumenter = instrumenter;
  }

  @Override
  public boolean shouldStart(Context parentContext, NettyConnectRequest request) {
    return instrumenter.shouldStart(parentContext, request);
  }

  @Override
  public Context start(Context parentContext, NettyConnectRequest request) {
    return instrumenter.start(parentContext, request);
  }

  @Override
  public void end(
      Context context, NettyConnectRequest request, Channel channel, @Nullable Throwable error) {
    instrumenter.end(context, request, channel, error);
  }
}
