/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.common.client;

import io.netty.channel.Channel;
import io.opentelemetry.context.Context;
import javax.annotation.Nullable;

public interface NettyConnectInstrumenter {

  boolean shouldStart(Context parentContext, NettyConnectRequest request);

  Context start(Context parentContext, NettyConnectRequest request);

  void end(
      Context context, NettyConnectRequest request, Channel channel, @Nullable Throwable error);
}
