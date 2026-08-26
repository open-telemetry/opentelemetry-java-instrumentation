/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ratpack.v1_7;

import static io.netty.handler.codec.http.HttpResponseStatus.CONTINUE;
import static io.netty.handler.codec.http.HttpResponseStatus.FOUND;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_0;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultHttpResponse;
import org.junit.jupiter.api.Test;
import ratpack.http.client.RequestSpec;

class RatpackHttpProtocolVersionTest {

  @Test
  void ignoresInformationalResponses() {
    RequestSpec request = mock(RequestSpec.class);
    EmbeddedChannel channel = channel();

    RatpackHttpProtocolVersion.attach(request, channel);
    channel.writeInbound(new DefaultHttpResponse(HTTP_1_0, CONTINUE));

    assertThat(RatpackHttpProtocolVersion.get(request)).isNull();
    assertThat(channel.pipeline().context(RatpackHttpProtocolVersion.class)).isNotNull();

    channel.writeInbound(new DefaultHttpResponse(HTTP_1_1, OK));

    assertThat(RatpackHttpProtocolVersion.get(request)).isEqualTo("1.1");
    assertThat(channel.pipeline().context(RatpackHttpProtocolVersion.class)).isNull();

    RatpackHttpProtocolVersion.clearRequest(request);
    channel.finishAndReleaseAll();
  }

  @Test
  void finalRedirectResponseWins() {
    RequestSpec request = mock(RequestSpec.class);
    EmbeddedChannel channel = channel();

    RatpackHttpProtocolVersion.attach(request, channel);
    channel.writeInbound(new DefaultHttpResponse(HTTP_1_0, FOUND));
    assertThat(RatpackHttpProtocolVersion.get(request)).isEqualTo("1");

    RatpackHttpProtocolVersion.attach(request, channel);
    channel.writeInbound(new DefaultHttpResponse(HTTP_1_1, OK));
    assertThat(RatpackHttpProtocolVersion.get(request)).isEqualTo("1.1");

    RatpackHttpProtocolVersion.clearRequest(request);
    channel.finishAndReleaseAll();
  }

  @Test
  void replacesStaleHandlerOnReusedChannel() {
    RequestSpec firstRequest = mock(RequestSpec.class);
    RequestSpec secondRequest = mock(RequestSpec.class);
    EmbeddedChannel channel = channel();

    RatpackHttpProtocolVersion.attach(firstRequest, channel);
    RatpackHttpProtocolVersion.attach(secondRequest, channel);

    assertThat(RatpackHttpProtocolVersion.get(firstRequest)).isNull();
    channel.writeInbound(new DefaultHttpResponse(HTTP_1_1, OK));
    assertThat(RatpackHttpProtocolVersion.get(secondRequest)).isEqualTo("1.1");

    RatpackHttpProtocolVersion.clearRequest(secondRequest);
    channel.finishAndReleaseAll();
  }

  private static EmbeddedChannel channel() {
    EmbeddedChannel channel = new EmbeddedChannel();
    channel.pipeline().addLast("redirect", new ChannelInboundHandlerAdapter());
    return channel;
  }
}
