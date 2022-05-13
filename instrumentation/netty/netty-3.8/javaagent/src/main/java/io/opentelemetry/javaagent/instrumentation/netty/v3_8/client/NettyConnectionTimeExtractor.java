/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v3_8.client;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.TimeExtractor;
import io.opentelemetry.javaagent.instrumentation.netty.common.NettyConnectionRequest;
import java.time.Instant;
import org.jboss.netty.channel.Channel;

class NettyConnectionTimeExtractor implements TimeExtractor<NettyConnectionRequest, Channel> {

  @Override
  public Instant extractStartTime(Context parentContext, NettyConnectionRequest request) {
    return request.timer().startTime();
  }

  @Override
  public Instant extractEndTime(Context context, NettyConnectionRequest request) {
    return request.timer().now();
  }
}
