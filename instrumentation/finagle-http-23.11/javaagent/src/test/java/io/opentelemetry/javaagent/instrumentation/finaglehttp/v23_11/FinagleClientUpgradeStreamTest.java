/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.finaglehttp.v23_11;

import static io.opentelemetry.semconv.HttpAttributes.HTTP_RESPONSE_STATUS_CODE;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http2.Http2FrameStream;
import io.netty.handler.codec.http2.Http2Stream;
import io.netty.handler.codec.http2.Http2StreamChannel;
import io.netty.handler.codec.http2.Http2StreamFrameToHttpObjectCodec;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.javaagent.testing.common.AgentClassLoaderAccess;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class FinagleClientUpgradeStreamTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  void laterStreamCannotAdoptUpgradeRequest() throws Exception {
    Class.forName(
        "com.twitter.finagle.http2.transport.common.H2StreamChannelInit$",
        true,
        FinagleClientUpgradeStreamTest.class.getClassLoader());
    EmbeddedChannel connection = new EmbeddedChannel();
    connection.pipeline().addLast(clientHandler());
    TestStreamChannel upgradeStream = new TestStreamChannel(connection, 1);
    TestStreamChannel laterStream = new TestStreamChannel(connection, 3);
    List<SpanContext> callbackContexts = new ArrayList<>();
    try {
      SpanContext upgradeParent =
          testing.runWithSpan(
              "upgrade-parent",
              () -> {
                connection.writeOutbound(request("/upgrade"));
                return Span.current().getSpanContext();
              });
      connection.writeInbound(
          new DefaultFullHttpResponse(
              HttpVersion.HTTP_1_1, HttpResponseStatus.SWITCHING_PROTOCOLS));

      upgradeStream.pipeline().addLast(clientStreamInitializer());
      laterStream.pipeline().addLast(clientStreamInitializer());
      addCallbackHandler(upgradeStream, callbackContexts);
      addCallbackHandler(laterStream, callbackContexts);

      SpanContext laterParent =
          testing.runWithSpan(
              "later-parent",
              () -> {
                laterStream.writeOutbound(request("/later"));
                return Span.current().getSpanContext();
              });
      fireResponse(laterStream, HttpResponseStatus.ACCEPTED);
      assertThat(callbackContexts).containsExactly(laterParent);

      fireResponse(upgradeStream, HttpResponseStatus.CREATED);
      assertThat(callbackContexts).containsExactly(laterParent, upgradeParent);

      connection.close();
      List<SpanData> spans = testing.spans();
      SpanData upgradeParentSpan = spanNamed(spans, "upgrade-parent");
      SpanData laterParentSpan = spanNamed(spans, "later-parent");
      List<SpanData> clientSpans =
          spans.stream().filter(span -> span.getKind() == SpanKind.CLIENT).collect(toList());
      assertThat(clientSpans).hasSize(2);

      SpanData upgradeClientSpan =
          clientSpans.stream()
              .filter(
                  span ->
                      Long.valueOf(201).equals(span.getAttributes().get(HTTP_RESPONSE_STATUS_CODE)))
              .findFirst()
              .orElseThrow(AssertionError::new);
      SpanData laterClientSpan =
          clientSpans.stream()
              .filter(
                  span ->
                      Long.valueOf(202).equals(span.getAttributes().get(HTTP_RESPONSE_STATUS_CODE)))
              .findFirst()
              .orElseThrow(AssertionError::new);
      assertThat(upgradeClientSpan.getParentSpanId()).isEqualTo(upgradeParentSpan.getSpanId());
      assertThat(laterClientSpan.getParentSpanId()).isEqualTo(laterParentSpan.getSpanId());
      assertThat(testing.spans().stream().filter(span -> span.getKind() == SpanKind.CLIENT))
          .hasSize(2);
    } finally {
      upgradeStream.finishAndReleaseAll();
      laterStream.finishAndReleaseAll();
      connection.finishAndReleaseAll();
    }
  }

  private static ChannelHandler clientHandler() throws Exception {
    Class<?> singletons =
        instrumentationClassLoader(
                "io.opentelemetry.javaagent.instrumentation.netty.v4_1.NettyInstrumentationModule")
            .loadClass(
                "io.opentelemetry.javaagent.instrumentation.netty.v4_1.NettyClientSingletons");
    Object factory = singletons.getMethod("clientHandlerFactory").invoke(null);
    return (ChannelHandler) factory.getClass().getMethod("createCombinedHandler").invoke(factory);
  }

  @SuppressWarnings("unchecked")
  private static ChannelInitializer<Channel> clientStreamInitializer() throws Exception {
    Class<?> helpers =
        instrumentationClassLoader(
                "io.opentelemetry.javaagent.instrumentation.finaglehttp.v23_11.FinagleHttpInstrumentationModule")
            .loadClass("io.opentelemetry.javaagent.instrumentation.finaglehttp.v23_11.Helpers");
    return (ChannelInitializer<Channel>)
        helpers
            .getMethod("wrapClient", ChannelInitializer.class)
            .invoke(
                null,
                new ChannelInitializer<Channel>() {
                  @Override
                  protected void initChannel(Channel channel) {
                    channel.pipeline().addLast(new Http2StreamFrameToHttpObjectCodec(false));
                  }
                });
  }

  private static ClassLoader instrumentationClassLoader(String moduleClassName) throws Exception {
    Class<?> registry =
        AgentClassLoaderAccess.loadClass(
            "io.opentelemetry.javaagent.tooling.instrumentation.indy.IndyModuleRegistry");
    return (ClassLoader)
        registry
            .getMethod("getInstrumentationClassLoader", String.class, ClassLoader.class)
            .invoke(null, moduleClassName, FinagleClientUpgradeStreamTest.class.getClassLoader());
  }

  private static void addCallbackHandler(
      EmbeddedChannel channel, List<SpanContext> callbackContexts) {
    channel
        .pipeline()
        .addLast(
            new ChannelInboundHandlerAdapter() {
              @Override
              public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                callbackContexts.add(Span.current().getSpanContext());
                super.channelRead(ctx, msg);
              }
            });
  }

  private static void fireResponse(EmbeddedChannel channel, HttpResponseStatus status) {
    ChannelHandlerContext codecContext =
        channel.pipeline().context(Http2StreamFrameToHttpObjectCodec.class);
    codecContext.fireChannelRead(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status));
  }

  private static DefaultFullHttpRequest request(String uri) {
    DefaultFullHttpRequest request =
        new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri);
    request.headers().set("host", "localhost");
    return request;
  }

  private static SpanData spanNamed(List<SpanData> spans, String name) {
    return spans.stream()
        .filter(span -> span.getName().equals(name))
        .findFirst()
        .orElseThrow(AssertionError::new);
  }

  private static final class TestStreamChannel extends EmbeddedChannel
      implements Http2StreamChannel {
    private final Channel parent;
    private final Http2FrameStream stream;

    TestStreamChannel(Channel parent, int streamId) {
      this.parent = parent;
      stream =
          new Http2FrameStream() {
            @Override
            public int id() {
              return streamId;
            }

            @Override
            public Http2Stream.State state() {
              return Http2Stream.State.OPEN;
            }
          };
    }

    @Override
    public Channel parent() {
      return parent;
    }

    @Override
    public Http2FrameStream stream() {
      return stream;
    }
  }
}
