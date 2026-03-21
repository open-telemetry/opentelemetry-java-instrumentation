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
  void peekFirstAndLastReturnCorrectElements() {
    EmbeddedChannel channel = new EmbeddedChannel();
    ServerContexts serverContexts = ServerContexts.getOrCreate(channel);

    ServerContext first =
        ServerContext.create(
            Context.root(),
            NettyCommonRequest.create(
                new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/first"), channel));
    ServerContext second =
        ServerContext.create(
            Context.root(),
            NettyCommonRequest.create(
                new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/second"), channel));

    serverContexts.addLast(first);
    serverContexts.addLast(second);

    // regression test: peekLast() was incorrectly calling peekFirst() and would return first
    assertThat(serverContexts.peekFirst()).isSameAs(first);
    assertThat(serverContexts.peekLast()).isSameAs(second);
  }
}
