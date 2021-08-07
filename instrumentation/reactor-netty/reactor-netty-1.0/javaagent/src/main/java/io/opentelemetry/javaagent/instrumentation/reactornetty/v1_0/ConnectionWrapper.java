/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactornetty.v1_0;

import static io.opentelemetry.javaagent.instrumentation.reactornetty.v1_0.ReactorNettyTracer.tracer;

import io.netty.channel.Channel;
import io.opentelemetry.context.Context;
import java.net.SocketAddress;
import reactor.core.publisher.Mono;

public class ConnectionWrapper {

  public static Mono<Channel> wrap(
      Context context, Context parentContext, SocketAddress remoteAddress, Mono<Channel> mono) {
    return mono.doOnError(
            throwable -> {
              tracer().endConnectionSpan(context, parentContext, remoteAddress, null, throwable);
            })
        .doOnSuccess(
            channel -> {
              if (context != null) {
                tracer().endConnectionSpan(context, parentContext, remoteAddress, channel, null);
              }
            });
  }
}
