/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.common.client;

import io.netty.channel.Channel;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import javax.annotation.Nullable;

final class NettyErrorOnlyConnectInstrumenter implements NettyConnectInstrumenter {

  private final Instrumenter<NettyConnectRequest, Channel> instrumenter;

  NettyErrorOnlyConnectInstrumenter(Instrumenter<NettyConnectRequest, Channel> instrumenter) {
    this.instrumenter = instrumenter;
  }

  @Override
  public boolean shouldStart(Context parentContext, NettyConnectRequest request) {
    // the "real" check is done on end() anyway
    return true;
  }

  @Override
  public Context start(Context parentContext, NettyConnectRequest request) {
    return parentContext;
  }

  @Override
  public void end(
      Context context, NettyConnectRequest request, Channel channel, @Nullable Throwable error) {
    if (error != null && instrumenter.shouldStart(context, request)) {
      Context connectContext = instrumenter.start(context, request);
      instrumenter.end(connectContext, request, channel, error);
    }
  }
}
