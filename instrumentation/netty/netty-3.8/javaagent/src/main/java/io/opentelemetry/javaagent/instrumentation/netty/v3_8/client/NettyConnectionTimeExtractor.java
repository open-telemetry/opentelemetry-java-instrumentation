/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v3_8.client;

import io.opentelemetry.instrumentation.api.instrumenter.TimeExtractor;
import io.opentelemetry.javaagent.instrumentation.netty.common.NettyConnectionRequest;
import java.time.Instant;
import javax.annotation.Nullable;
import org.jboss.netty.channel.Channel;

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
