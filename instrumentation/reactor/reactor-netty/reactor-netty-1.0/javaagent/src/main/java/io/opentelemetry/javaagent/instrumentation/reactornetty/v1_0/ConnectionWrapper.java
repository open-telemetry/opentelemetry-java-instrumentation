/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactornetty.v1_0;

import static io.opentelemetry.javaagent.instrumentation.reactornetty.v1_0.ReactorNettySingletons.connectionInstrumenter;

import io.netty.channel.Channel;
import io.netty.channel.ChannelPromise;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import javax.annotation.Nullable;
import reactor.core.publisher.Mono;

public final class ConnectionWrapper {

  private static final VirtualField<ChannelPromise, ConnectionRequestAndContext>
      requestAndContextField =
          VirtualField.find(ChannelPromise.class, ConnectionRequestAndContext.class);

  public static Mono<Channel> wrap(Mono<Channel> mono) {
    // if we didn't attach the connection context&request then we have nothing to instrument
    if (!(mono instanceof ChannelPromise)) {
      return mono;
    }
    return mono.doOnError(error -> end(mono, null, error))
        .doOnSuccess(channel -> end(mono, channel, null))
        .doOnCancel(() -> end(mono, null, null));
  }

  private static void end(
      Mono<Channel> mono, @Nullable Channel channel, @Nullable Throwable error) {
    ConnectionRequestAndContext requestAndContext =
        requestAndContextField.get((ChannelPromise) mono);
    if (requestAndContext == null) {
      return;
    }
    connectionInstrumenter()
        .end(requestAndContext.context(), requestAndContext.request(), channel, error);
  }

  private ConnectionWrapper() {}
}
