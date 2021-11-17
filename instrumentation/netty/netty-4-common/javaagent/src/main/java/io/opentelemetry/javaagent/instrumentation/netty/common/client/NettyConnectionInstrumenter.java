/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.common.client;

import io.netty.channel.Channel;
import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.instrumentation.netty.common.NettyConnectionRequest;
import javax.annotation.Nullable;

public interface NettyConnectionInstrumenter {

  boolean shouldStart(Context parentContext, NettyConnectionRequest request);

  Context start(Context parentContext, NettyConnectionRequest request);

  void end(
      Context context, NettyConnectionRequest request, Channel channel, @Nullable Throwable error);
}
