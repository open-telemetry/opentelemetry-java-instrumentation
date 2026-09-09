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
import static org.junit.jupiter.params.provider.Arguments.argumentSet;
import static org.mockito.Mockito.mock;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpVersion;
import io.opentelemetry.instrumentation.ratpack.v1_7.internal.RatpackHttpProtocolVersion;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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
    assertThat(RatpackHttpProtocolVersion.get(request)).isNull();
    channel.finishAndReleaseAll();
  }

  @Test
  void finalRedirectResponseWins() {
    RequestSpec request = mock(RequestSpec.class);
    EmbeddedChannel channel = channel();

    RatpackHttpProtocolVersion.attach(request, channel);
    channel.writeInbound(new DefaultHttpResponse(HTTP_1_0, FOUND));
    assertThat(RatpackHttpProtocolVersion.get(request)).isEqualTo("1.0");

    RatpackHttpProtocolVersion.attach(request, channel);
    channel.writeInbound(new DefaultHttpResponse(HTTP_1_1, OK));
    assertThat(RatpackHttpProtocolVersion.get(request)).isEqualTo("1.1");

    RatpackHttpProtocolVersion.clearRequest(request);
    assertThat(RatpackHttpProtocolVersion.get(request)).isNull();
    channel.finishAndReleaseAll();
  }

  @ParameterizedTest
  @MethodSource("protocolVersions")
  void capturesProtocolVersion(HttpVersion version, String expected) {
    RequestSpec request = mock(RequestSpec.class);
    EmbeddedChannel channel = channel();

    RatpackHttpProtocolVersion.attach(request, channel);
    channel.writeInbound(new DefaultHttpResponse(version, OK));

    assertThat(RatpackHttpProtocolVersion.get(request)).isEqualTo(expected);

    RatpackHttpProtocolVersion.clearRequest(request);
    channel.finishAndReleaseAll();
  }

  private static Stream<Arguments> protocolVersions() {
    return Stream.of(
        argumentSet("HTTP/1.0", HTTP_1_0, "1.0"),
        argumentSet("HTTP/1.1", HTTP_1_1, "1.1"),
        argumentSet("HTTP/2.0", new HttpVersion("HTTP/2.0", true), "2"),
        argumentSet("HTTP/3.0", new HttpVersion("HTTP/3.0", true), "3"));
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

    RatpackHttpProtocolVersion.clearRequest(firstRequest);
    RatpackHttpProtocolVersion.clearRequest(secondRequest);
    channel.finishAndReleaseAll();
  }

  @Test
  void clearRequestRemovesPendingHandler() {
    RequestSpec request = mock(RequestSpec.class);
    EmbeddedChannel channel = channel();

    RatpackHttpProtocolVersion.attach(request, channel);
    RatpackHttpProtocolVersion.clearRequest(request);

    assertThat(RatpackHttpProtocolVersion.get(request)).isNull();
    assertThat(channel.pipeline().context(RatpackHttpProtocolVersion.class)).isNull();

    channel.finishAndReleaseAll();
  }

  @Test
  void channelInactiveRemovesHandlerAndForwardsEvent() {
    RequestSpec request = mock(RequestSpec.class);
    AtomicBoolean inactive = new AtomicBoolean();
    EmbeddedChannel channel =
        channel(
            new ChannelInboundHandlerAdapter() {
              @Override
              public void channelInactive(ChannelHandlerContext context) {
                inactive.set(true);
                context.fireChannelInactive();
              }
            });

    RatpackHttpProtocolVersion.attach(request, channel);
    channel.close();

    assertThat(channel.pipeline().context(RatpackHttpProtocolVersion.class)).isNull();
    assertThat(inactive).isTrue();

    RatpackHttpProtocolVersion.clearRequest(request);
    channel.finishAndReleaseAll();
  }

  @Test
  void exceptionCaughtRemovesHandlerAndForwardsEvent() {
    RequestSpec request = mock(RequestSpec.class);
    RuntimeException error = new RuntimeException();
    AtomicReference<Throwable> forwardedError = new AtomicReference<>();
    EmbeddedChannel channel =
        channel(
            new ChannelInboundHandlerAdapter() {
              @Override
              public void exceptionCaught(ChannelHandlerContext context, Throwable cause) {
                forwardedError.set(cause);
              }
            });

    RatpackHttpProtocolVersion.attach(request, channel);
    channel.pipeline().fireExceptionCaught(error);

    assertThat(channel.pipeline().context(RatpackHttpProtocolVersion.class)).isNull();
    assertThat(forwardedError).hasValue(error);

    RatpackHttpProtocolVersion.clearRequest(request);
    channel.finishAndReleaseAll();
  }

  private static EmbeddedChannel channel() {
    return channel(new ChannelInboundHandlerAdapter());
  }

  private static EmbeddedChannel channel(ChannelInboundHandlerAdapter redirectHandler) {
    EmbeddedChannel channel = new EmbeddedChannel();
    channel.pipeline().addLast("redirect", redirectHandler);
    return channel;
  }
}
