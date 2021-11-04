/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactornetty.v1_0;

import static io.opentelemetry.javaagent.instrumentation.reactornetty.v1_0.ReactorNettySingletons.connectionInstrumenter;

import io.netty.channel.Channel;
import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.instrumentation.netty.common.NettyConnectionRequest;
import reactor.core.publisher.Mono;

public class ConnectionWrapper {

  public static Mono<Channel> wrap(
      Context context, NettyConnectionRequest request, Mono<Channel> mono) {
    return mono.doOnError(error -> connectionInstrumenter().end(context, request, null, error))
        .doOnSuccess(channel -> connectionInstrumenter().end(context, request, channel, null));
  }
}
