/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.netty.v4_1.internal;

import static org.assertj.core.api.Assertions.assertThat;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.netty.common.v4_0.internal.NettyCommonRequest;
import org.junit.jupiter.api.Test;

class ServerContextsTest {

  @Test
  void clearsAndStopsTrackingWhenPipeliningLimitIsExceeded() {
    EmbeddedChannel channel = new EmbeddedChannel();
    ServerContexts serverContexts = ServerContexts.getOrCreate(channel);

    for (int i = 0; i < 1000; i++) {
      serverContexts.addLast(newServerContext(channel, "/" + i));
    }

    serverContexts.addLast(newServerContext(channel, "/overflow"));

    assertThat(serverContexts.peekFirst()).isNull();

    serverContexts.addLast(newServerContext(channel, "/ignored"));

    assertThat(serverContexts.peekFirst()).isNull();
  }

  private static ServerContext newServerContext(EmbeddedChannel channel, String path) {
    return ServerContext.create(
        Context.root(),
        NettyCommonRequest.create(
            new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, path), channel));
  }
}
