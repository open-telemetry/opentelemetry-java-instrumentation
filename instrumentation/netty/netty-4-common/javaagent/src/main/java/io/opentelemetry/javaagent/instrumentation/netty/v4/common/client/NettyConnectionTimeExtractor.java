/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4.common.client;

import io.netty.channel.Channel;
import io.opentelemetry.instrumentation.api.instrumenter.TimeExtractor;
import io.opentelemetry.javaagent.instrumentation.netty.common.NettyConnectionRequest;
import java.time.Instant;
import javax.annotation.Nullable;

class NettyConnectionTimeExtractor implements TimeExtractor<NettyConnectionRequest, Channel> {

  @Override
  public Instant extractStartTime(NettyConnectionRequest request) {
    return request.timer().startTime();
  }

  @Override
  public Instant extractEndTime(
      NettyConnectionRequest request, @Nullable Channel channel, @Nullable Throwable error) {
    return request.timer().now();
  }
}
