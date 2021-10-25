/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactornetty.v1_0;

import static io.opentelemetry.javaagent.instrumentation.reactornetty.v1_0.ReactorNettySingletons.connectInstrumenter;

import io.netty.channel.Channel;
import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.instrumentation.netty.common.client.NettyConnectRequest;
import reactor.core.publisher.Mono;

public class ConnectionWrapper {

  public static Mono<Channel> wrap(
      Context context, NettyConnectRequest request, Mono<Channel> mono) {
    return mono.doOnError(error -> connectInstrumenter().end(context, request, null, error))
        .doOnSuccess(channel -> connectInstrumenter().end(context, request, channel, null));
  }
}
