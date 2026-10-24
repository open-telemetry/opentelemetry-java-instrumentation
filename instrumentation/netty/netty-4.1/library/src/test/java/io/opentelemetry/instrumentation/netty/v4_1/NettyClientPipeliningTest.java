/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.netty.v4_1;

import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_TYPE;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_RESPONSE_STATUS_CODE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCountUtil;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.internal.SpanKey;
import io.opentelemetry.instrumentation.netty.common.v4_0.internal.NettyCommonRequest;
import io.opentelemetry.instrumentation.netty.common.v4_0.internal.client.NettyClientInstrumenterBuilderFactory;
import io.opentelemetry.instrumentation.netty.common.v4_0.internal.client.NettyClientInstrumenterFactory;
import io.opentelemetry.instrumentation.netty.common.v4_0.internal.client.NettyConnectionInstrumentationFlag;
import io.opentelemetry.instrumentation.netty.v4_1.internal.AttributeKeys;
import io.opentelemetry.instrumentation.netty.v4_1.internal.Experimental;
import io.opentelemetry.instrumentation.netty.v4_1.internal.client.ClientContexts;
import io.opentelemetry.instrumentation.netty.v4_1.internal.client.NettyClientHandlerFactory;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class NettyClientPipeliningTest {
  @RegisterExtension
  private static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Test
  void unrelatedExceptionDoesNotAllocateClientState() {
    EmbeddedChannel channel = new EmbeddedChannel();
    try {
      AttributeKey<ClientContexts> key =
          AttributeKey.valueOf(ClientContexts.class, "client-contexts");
      ClientContexts.fail(
          channel,
          Instrumenter.<NettyCommonRequest, HttpResponse>builder(
                  testing.getOpenTelemetry(), "test", request -> "GET")
              .buildInstrumenter(),
          new IOException("unrelated exception"));
      assertThat(channel.hasAttr(key)).isFalse();
    } finally {
      channel.finishAndReleaseAll();
    }
  }

  private static EmbeddedChannel channel(ChannelOutboundHandlerAdapter outbound) {
    return channel(outbound, false);
  }

  private static EmbeddedChannel channel(ChannelOutboundHandlerAdapter outbound, boolean events) {
    NettyClientTelemetryBuilder builder = NettyClientTelemetry.builder(testing.getOpenTelemetry());
    Experimental.setEmitExperimentalTelemetry(builder, events);
    return new EmbeddedChannel(outbound, builder.build().createCombinedHandler());
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void untracedRequestsKeepTheirResponsePosition(boolean aws) {
    List<Context> contexts = new ArrayList<>();
    EmbeddedChannel channel =
        channel(
            new ChannelOutboundHandlerAdapter() {
              @Override
              public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise)
                  throws Exception {
                contexts.add(Context.current());
                super.write(ctx, msg, promise);
              }
            });
    try {
      channel.writeOutbound(request("/first"));
      DefaultFullHttpRequest untraced = request("/untraced");
      if (aws) {
        untraced.headers().set("amz-sdk-invocation-id", "signed");
        channel.writeOutbound(untraced);
      } else {
        try (Scope ignored = contexts.get(0).makeCurrent()) {
          channel.writeOutbound(untraced);
        }
      }
      channel.writeOutbound(request("/third"));
      assertThat(untraced.headers().contains("traceparent")).isFalse();
      response(channel, 201);
      response(channel, 202);
      response(channel, 203);
      assertThat(testing.spans()).hasSize(2);
      assertThat(testing.spans().get(0).getAttributes().get(HTTP_RESPONSE_STATUS_CODE))
          .isEqualTo(201);
      assertThat(testing.spans().get(1).getAttributes().get(HTTP_RESPONSE_STATUS_CODE))
          .isEqualTo(203);
    } finally {
      channel.finishAndReleaseAll();
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void untracedFirstRequestKeepsItsResponsePosition(boolean aws) {
    EmbeddedChannel channel = channel(new ChannelOutboundHandlerAdapter());
    try {
      DefaultFullHttpRequest untraced = request("/untraced");
      if (aws) {
        untraced.headers().set("amz-sdk-invocation-id", "signed");
        channel.writeOutbound(untraced);
      } else {
        Context suppressed = SpanKey.HTTP_CLIENT.storeInContext(Context.root(), Span.getInvalid());
        try (Scope ignored = suppressed.makeCurrent()) {
          channel.writeOutbound(untraced);
        }
      }
      channel.writeOutbound(request("/traced"));
      assertThat(untraced.headers().contains("traceparent")).isFalse();
      response(channel, 201);
      assertThat(testing.spans()).isEmpty();
      response(channel, 202);
      assertThat(testing.spans()).hasSize(1);
      assertThat(testing.spans().get(0).getAttributes().get(HTTP_RESPONSE_STATUS_CODE))
          .isEqualTo(202);
    } finally {
      channel.finishAndReleaseAll();
    }
  }

  @Test
  void streamingResponsesUseTheirOwnParentThroughout() {
    EmbeddedChannel channel = channel(new ChannelOutboundHandlerAdapter());
    List<SpanContext> callbacks = new ArrayList<>();
    List<SpanContext> parents = new ArrayList<>();
    channel
        .pipeline()
        .addLast(
            new ChannelInboundHandlerAdapter() {
              @Override
              public void channelRead(ChannelHandlerContext ctx, Object msg) {
                callbacks.add(Span.current().getSpanContext());
                ReferenceCountUtil.release(msg);
              }
            });
    try {
      for (int i = 0; i < 2; i++) {
        Span parent =
            testing.getOpenTelemetry().getTracer("test").spanBuilder("parent-" + i).startSpan();
        parents.add(parent.getSpanContext());
        NettyClientTelemetry.setParentContext(channel, Context.root().with(parent));
        channel.writeOutbound(request("/request-" + i));
        parent.end();
      }
      for (int i = 0; i < 2; i++) {
        channel.writeInbound(
            new DefaultHttpResponse(HTTP_1_1, HttpResponseStatus.valueOf(201 + i)));
        channel.writeInbound(new DefaultHttpContent(Unpooled.copiedBuffer("body", UTF_8)));
        channel.writeInbound(LastHttpContent.EMPTY_LAST_CONTENT);
      }
      assertThat(callbacks)
          .containsExactly(
              parents.get(0),
              parents.get(0),
              parents.get(0),
              parents.get(1),
              parents.get(1),
              parents.get(1));
      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("parent-0"),
                  span -> span.hasName("GET").hasParent(trace.getSpan(0))),
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("parent-1"),
                  span -> span.hasName("GET").hasParent(trace.getSpan(0))));
    } finally {
      channel.finishAndReleaseAll();
    }
  }

  @Test
  void overflowQueuePromotesRequestsAndKeepsSequentialKeepAlive() {
    EmbeddedChannel channel = channel(new ChannelOutboundHandlerAdapter());
    try {
      channel.writeOutbound(request("/first"), request("/second"), request("/third"));
      assertThat(channel.attr(AttributeKeys.CLIENT_CONTEXT).get()).isNotNull();

      response(channel, 201);
      assertThat(testing.spans()).hasSize(1);
      assertThat(channel.attr(AttributeKeys.CLIENT_CONTEXT).get()).isNotNull();

      response(channel, 202);
      assertThat(testing.spans()).hasSize(2);
      assertThat(channel.attr(AttributeKeys.CLIENT_CONTEXT).get()).isNotNull();

      response(channel, 203);
      assertThat(testing.spans()).hasSize(3);
      assertThat(channel.attr(AttributeKeys.CLIENT_CONTEXT).get()).isNull();

      channel.writeOutbound(request("/sequential"));
      response(channel, 204);
      assertThat(testing.spans()).hasSize(4);
      assertThat(testing.spans())
          .extracting(span -> span.getAttributes().get(HTTP_RESPONSE_STATUS_CODE))
          .containsExactly(201L, 202L, 203L, 204L);
    } finally {
      channel.finishAndReleaseAll();
    }
  }

  @Test
  void requestFromResponseCallbackKeepsReentrantRequestContextPublished() {
    EmbeddedChannel channel = channel(new ChannelOutboundHandlerAdapter());
    List<SpanContext> parents = new ArrayList<>();
    List<SpanContext> callbackContexts = new ArrayList<>();
    channel
        .pipeline()
        .addLast(
            new ChannelInboundHandlerAdapter() {
              private int responses;

              @Override
              public void channelRead(ChannelHandlerContext ctx, Object msg) {
                callbackContexts.add(Span.current().getSpanContext());
                ReferenceCountUtil.release(msg);
                if (responses++ == 0) {
                  try (Scope ignored = Context.root().makeCurrent()) {
                    testing.runWithSpan(
                        "second",
                        () -> {
                          parents.add(Span.current().getSpanContext());
                          ctx.channel().writeAndFlush(request("/second"));
                        });
                  }
                }
              }
            });
    try {
      testing.runWithSpan(
          "first",
          () -> {
            parents.add(Span.current().getSpanContext());
            channel.writeOutbound(request("/first"));
          });

      response(channel, 201);
      response(channel, 202);

      assertThat(callbackContexts).containsExactlyElementsOf(parents);
      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("first"),
                  span -> span.hasName("GET").hasParent(trace.getSpan(0))),
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("second"),
                  span -> span.hasName("GET").hasParent(trace.getSpan(0))));
    } finally {
      channel.finishAndReleaseAll();
    }
  }

  @ParameterizedTest
  @CsvSource({"false, false", "false, true", "true, false", "true, true"})
  void asynchronousWriteFailureDisablesCorrelation(boolean voidPromise, boolean delivered) {
    List<ChannelPromise> promises = new ArrayList<>();
    EmbeddedChannel channel =
        channel(
            new ChannelOutboundHandlerAdapter() {
              @Override
              public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise)
                  throws Exception {
                if (promises.isEmpty()) {
                  promises.add(promise);
                  if (delivered) {
                    super.write(ctx, msg, ctx.newPromise());
                  } else {
                    ReferenceCountUtil.release(msg);
                  }
                } else {
                  super.write(ctx, msg, promise);
                }
              }
            });
    channel
        .pipeline()
        .addLast(
            new ChannelInboundHandlerAdapter() {
              @Override
              public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {}
            });
    try {
      channel.write(request("/failed"), voidPromise ? channel.voidPromise() : channel.newPromise());
      channel.writeOutbound(request("/success"));
      IOException failure = new IOException("failed write");
      promises.get(0).setFailure(failure);
      channel.runPendingTasks();
      if (delivered) {
        response(channel, 201);
      }
      response(channel, 202);
      DefaultFullHttpRequest subsequent = request("/subsequent");
      channel.writeOutbound(subsequent);
      assertThat(subsequent.headers().contains("traceparent")).isFalse();
      response(channel, 203);
      DefaultFullHttpRequest signed = request("/signed");
      signed.headers().set("amz-sdk-invocation-id", "signed");
      signed.headers().set("traceparent", "signed-trace-header");
      channel.writeOutbound(signed);
      assertThat(signed.headers().get("traceparent")).isEqualTo("signed-trace-header");
      response(channel, 204);
      assertThat(channel.isActive()).isTrue();
      channel.close();
      assertThat(testing.spans()).hasSize(2);
      assertThat(testing.spans())
          .allSatisfy(
              span -> {
                assertThat(span.getEvents()).hasSize(1);
                assertThat(span.getAttributes().get(HTTP_RESPONSE_STATUS_CODE)).isNull();
              });
    } finally {
      channel.finishAndReleaseAll();
    }
  }

  @Test
  void informationalResponsesDoNotConsumeRequests() {
    EmbeddedChannel channel = channel(new ChannelOutboundHandlerAdapter());
    try {
      channel.writeOutbound(request("/first"), request("/second"));
      response(channel, 100);
      channel.writeInbound(new DefaultHttpResponse(HTTP_1_1, HttpResponseStatus.valueOf(103)));
      channel.writeInbound(LastHttpContent.EMPTY_LAST_CONTENT);
      assertThat(testing.spans()).isEmpty();
      response(channel, 201);
      response(channel, 202);
      assertThat(testing.spans()).hasSize(2);
      assertThat(testing.spans().get(0).getAttributes().get(HTTP_RESPONSE_STATUS_CODE))
          .isEqualTo(201);
      assertThat(testing.spans().get(1).getAttributes().get(HTTP_RESPONSE_STATUS_CODE))
          .isEqualTo(202);
    } finally {
      channel.finishAndReleaseAll();
    }
  }

  @ParameterizedTest
  @CsvSource({"false, false", "false, true", "true, false", "true, true"})
  void switchingProtocolsRetainsRequest(boolean fullResponse, boolean events) {
    EmbeddedChannel channel = channel(new ChannelOutboundHandlerAdapter(), events);
    try {
      channel.writeOutbound(request("/upgrade"));
      if (fullResponse) {
        response(channel, 101);
      } else {
        channel.writeInbound(
            new DefaultHttpResponse(HTTP_1_1, HttpResponseStatus.SWITCHING_PROTOCOLS));
        channel.writeInbound(LastHttpContent.EMPTY_LAST_CONTENT);
      }
      assertThat(testing.spans()).isEmpty();
      assertThat(channel.attr(AttributeKeys.CLIENT_CONTEXT).get()).isNotNull();
      response(channel, 200);
      channel.close();
      assertThat(testing.spans()).hasSize(1);
      if (events) {
        assertThat(testing.spans().get(0).getEvents())
            .extracting(event -> event.getName())
            .containsExactly("http.response.status_code.101.upgrade");
      } else {
        assertThat(testing.spans().get(0).getEvents()).isEmpty();
      }
      assertThat(testing.spans().get(0).getAttributes().get(HTTP_RESPONSE_STATUS_CODE))
          .isEqualTo(200);
    } finally {
      channel.finishAndReleaseAll();
    }
  }

  @ParameterizedTest
  @CsvSource({"false, false", "false, true", "true, false", "true, true"})
  void delayedWriteFailureDoesNotFailAnotherRequest(boolean voidPromise, boolean bodyWrite) {
    List<ChannelPromise> delayedPromises = new ArrayList<>();
    List<Throwable> visibleFailures = new ArrayList<>();
    boolean[] delayed = {false};
    EmbeddedChannel channel =
        channel(
            new ChannelOutboundHandlerAdapter() {
              @Override
              public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise)
                  throws Exception {
                boolean targetWrite =
                    bodyWrite ? msg instanceof HttpContent : msg instanceof HttpRequest;
                if (!delayed[0] && targetWrite) {
                  delayed[0] = true;
                  delayedPromises.add(promise);
                  super.write(ctx, msg, ctx.newPromise());
                } else {
                  super.write(ctx, msg, promise);
                }
              }
            });
    Instrumenter<NettyCommonRequest, HttpResponse> instrumenter =
        new NettyClientInstrumenterFactory(
                NettyClientInstrumenterBuilderFactory.create(
                    "io.opentelemetry.netty-4.1", testing.getOpenTelemetry()),
                NettyConnectionInstrumentationFlag.DISABLED,
                NettyConnectionInstrumentationFlag.DISABLED)
            .instrumenter();
    channel
        .pipeline()
        .addLast(
            new ChannelInboundHandlerAdapter() {
              @Override
              public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                visibleFailures.add(cause);
                ClientContexts.fail(ctx.channel(), instrumenter, cause);
              }
            });
    List<SpanContext> parents = new ArrayList<>();
    try {
      testing.runWithSpan(
          "first",
          () -> {
            parents.add(Span.current().getSpanContext());
            if (bodyWrite) {
              channel.writeAndFlush(new DefaultHttpRequest(HTTP_1_1, HttpMethod.POST, "/first"));
              channel.writeAndFlush(
                  new DefaultLastHttpContent(),
                  voidPromise ? channel.voidPromise() : channel.newPromise());
            } else {
              channel.writeAndFlush(
                  request("/first"), voidPromise ? channel.voidPromise() : channel.newPromise());
            }
          });
      testing.runWithSpan(
          "second",
          () -> {
            parents.add(Span.current().getSpanContext());
            channel.writeOutbound(request("/second"));
          });

      response(channel, 201);
      assertThat(delayedPromises).hasSize(1);
      IOException failure = new IOException("late write failure");
      delayedPromises.get(0).setFailure(failure);
      channel.runPendingTasks();
      response(channel, 202);

      testing.runWithSpan(
          "third",
          () -> {
            parents.add(Span.current().getSpanContext());
            DefaultFullHttpRequest subsequent = request("/third");
            channel.writeOutbound(subsequent);
            assertThat(subsequent.headers().contains("traceparent")).isTrue();
          });
      response(channel, 203);

      assertThat(channel.isActive()).isTrue();
      if (voidPromise) {
        assertThat(visibleFailures).containsExactly(failure);
      } else {
        assertThat(visibleFailures).isEmpty();
      }
      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("first"),
                  span ->
                      span.hasName(bodyWrite ? "POST" : "GET")
                          .hasParent(trace.getSpan(0))
                          .hasAttributesSatisfying(
                              attributes ->
                                  assertThat(attributes.get(HTTP_RESPONSE_STATUS_CODE))
                                      .isEqualTo(201L))),
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("second"),
                  span ->
                      span.hasName("GET")
                          .hasParent(trace.getSpan(0))
                          .hasAttributesSatisfying(
                              attributes ->
                                  assertThat(attributes.get(HTTP_RESPONSE_STATUS_CODE))
                                      .isEqualTo(202L))),
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("third"),
                  span ->
                      span.hasName("GET")
                          .hasParent(trace.getSpan(0))
                          .hasAttributesSatisfying(
                              attributes ->
                                  assertThat(attributes.get(HTTP_RESPONSE_STATUS_CODE))
                                      .isEqualTo(203L))));
      assertThat(testing.spans()).allSatisfy(span -> assertThat(span.getEvents()).isEmpty());
      assertThat(parents).hasSize(3);
    } finally {
      channel.finishAndReleaseAll();
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void websocketUpgradeEndsOnClose(boolean fullResponse) {
    EmbeddedChannel channel = channel(new ChannelOutboundHandlerAdapter(), true);
    try {
      channel.writeOutbound(request("/websocket"));
      if (fullResponse) {
        response(channel, 101);
      } else {
        channel.writeInbound(
            new DefaultHttpResponse(HTTP_1_1, HttpResponseStatus.SWITCHING_PROTOCOLS));
        channel.writeInbound(LastHttpContent.EMPTY_LAST_CONTENT);
      }
      assertThat(testing.spans()).isEmpty();
      channel.close();
      assertThat(testing.spans()).hasSize(1);
      assertThat(testing.spans().get(0).getEvents())
          .extracting(event -> event.getName())
          .containsExactly("http.response.status_code.101.upgrade");
      assertThat(testing.spans().get(0).getAttributes().get(HTTP_RESPONSE_STATUS_CODE))
          .isEqualTo(fullResponse ? null : 101L);
    } finally {
      channel.finishAndReleaseAll();
    }
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        "HTTP/1.1 200 OK\r\nContent-Length: 10\r\n\r\nbody",
        "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n4\r\nbody\r\n"
      })
  void remoteInactiveEndsIncompleteResponsesWithError(String response) {
    EmbeddedChannel channel =
        new EmbeddedChannel(
            new HttpClientCodec(),
            NettyClientTelemetry.builder(testing.getOpenTelemetry())
                .build()
                .createCombinedHandler());
    try {
      channel.writeOutbound(request("/first"), request("/second"));
      if (!response.isEmpty()) {
        channel.writeInbound(Unpooled.copiedBuffer(response, UTF_8));
      }
      assertThat(testing.spans()).isEmpty();
      channel.pipeline().fireChannelInactive();
      assertThat(testing.spans()).hasSize(2);
      assertThat(testing.spans())
          .allSatisfy(
              span -> {
                assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
                assertThat(span.getEvents()).hasSize(1);
                assertThat(span.getEvents().get(0).getAttributes().get(EXCEPTION_TYPE))
                    .isEqualTo(ClosedChannelException.class.getName());
              });
      channel.pipeline().fireChannelInactive();
      assertThat(testing.spans()).hasSize(2);
    } finally {
      channel.finishAndReleaseAll();
    }
  }

  @Test
  void remoteWebsocketCloseRemainsNormal() {
    EmbeddedChannel channel =
        new EmbeddedChannel(
            new HttpClientCodec(),
            NettyClientTelemetry.builder(testing.getOpenTelemetry())
                .build()
                .createCombinedHandler());
    try {
      channel.writeOutbound(request("/websocket"));
      channel.writeInbound(
          Unpooled.copiedBuffer(
              "HTTP/1.1 101 Switching Protocols\r\nConnection: Upgrade\r\n"
                  + "Upgrade: websocket\r\nSec-WebSocket-Accept: accepted\r\n\r\n",
              UTF_8));
      assertThat(testing.spans()).isEmpty();
      channel.pipeline().fireChannelInactive();
      assertThat(testing.spans()).hasSize(1);
      assertThat(testing.spans().get(0).getStatus().getStatusCode()).isEqualTo(StatusCode.UNSET);
      assertThat(testing.spans().get(0).getEvents()).isEmpty();
    } finally {
      channel.finishAndReleaseAll();
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"exception", "inactive", "close"})
  void pendingRequestsEndOnceOnExceptionOrInactive(String event) {
    EmbeddedChannel channel = channel(new ChannelOutboundHandlerAdapter());
    channel
        .pipeline()
        .addLast(
            new ChannelInboundHandlerAdapter() {
              @Override
              public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {}
            });
    try {
      channel.writeOutbound(request("/first"), request("/second"));
      if (event.equals("exception")) {
        channel.pipeline().fireExceptionCaught(new IOException("channel failed"));
        DefaultFullHttpRequest subsequent = request("/subsequent");
        channel.writeOutbound(subsequent);
        assertThat(subsequent.headers().contains("traceparent")).isFalse();
        // Forward responses without assigning them after an exception can lose framing.
        response(channel, 201);
        response(channel, 202);
        assertThat(channel.isActive()).isTrue();
      } else if (event.equals("inactive")) {
        channel.pipeline().fireChannelInactive();
      } else {
        channel.close();
      }
      channel.close();
      assertThat(testing.spans()).hasSize(2);
      if (event.equals("close")) {
        assertThat(testing.spans())
            .allSatisfy(
                span -> assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.UNSET));
      } else {
        assertThat(testing.spans())
            .allSatisfy(
                span -> assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR));
      }
      assertThat(channel.attr(AttributeKeys.CLIENT_CONTEXT).get()).isNull();
      assertThat(channel.attr(AttributeKeys.CLIENT_PARENT_CONTEXT).get()).isNull();
    } finally {
      channel.finishAndReleaseAll();
    }
  }

  @Test
  void exceptionParentIsNotReusedAfterCorrelationIsDisabled() {
    EmbeddedChannel channel = channel(new ChannelOutboundHandlerAdapter());
    List<SpanContext> callbacks = new ArrayList<>();
    channel
        .pipeline()
        .addLast(
            new ChannelInboundHandlerAdapter() {
              @Override
              public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                callbacks.add(Span.current().getSpanContext());
              }
            });
    Span parent = testing.getOpenTelemetry().getTracer("test").spanBuilder("parent").startSpan();
    Span subsequentParent =
        testing.getOpenTelemetry().getTracer("test").spanBuilder("subsequent-parent").startSpan();
    try {
      NettyClientTelemetry.setParentContext(channel, Context.root().with(parent));
      channel.writeOutbound(request("/first"));
      IOException failure = new IOException("decoder failed");
      channel.pipeline().fireExceptionCaught(failure);
      channel.pipeline().fireExceptionCaught(new IOException("another handler failed"));
      DefaultFullHttpRequest subsequent = request("/subsequent");
      try (Scope ignored = Context.root().with(subsequentParent).makeCurrent()) {
        channel.writeOutbound(subsequent);
        channel.pipeline().fireExceptionCaught(failure);
      }
      assertThat(subsequent.headers().contains("traceparent")).isFalse();
      assertThat(callbacks)
          .containsExactly(
              parent.getSpanContext(),
              Span.getInvalid().getSpanContext(),
              subsequentParent.getSpanContext());
      assertThat(channel.isActive()).isTrue();
    } finally {
      parent.end();
      subsequentParent.end();
      channel.finishAndReleaseAll();
    }
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent"),
                span -> span.hasName("GET").hasParent(trace.getSpan(0))),
        trace -> trace.hasSpansSatisfyingExactly(span -> span.hasName("subsequent-parent")));
  }

  @Test
  void downstreamExceptionAfterResponseDoesNotDisableCorrelationWithPipelinedRequest() {
    Instrumenter<NettyCommonRequest, HttpResponse> instrumenter =
        new NettyClientInstrumenterFactory(
                NettyClientInstrumenterBuilderFactory.create(
                    "io.opentelemetry.netty-4.1", testing.getOpenTelemetry()),
                NettyConnectionInstrumentationFlag.DISABLED,
                NettyConnectionInstrumentationFlag.DISABLED)
            .instrumenter();
    EmbeddedChannel channel = channel(new ChannelOutboundHandlerAdapter());
    IOException failure = new IOException("downstream handler failed");
    List<SpanContext> callbackParents = new ArrayList<>();
    List<SpanContext> parents = new ArrayList<>();
    boolean[] raised = {false};
    channel
        .pipeline()
        .addLast(
            new ChannelInboundHandlerAdapter() {
              @Override
              public void channelRead(ChannelHandlerContext ctx, Object msg) {
                callbackParents.add(Span.current().getSpanContext());
                int previousDepth = ClientContexts.enterChannelRead(ctx.channel(), msg);
                try {
                  ReferenceCountUtil.release(msg);
                  if (!raised[0]) {
                    raised[0] = true;
                    ctx.fireExceptionCaught(failure);
                  }
                } finally {
                  ClientContexts.exitChannelRead(previousDepth);
                }
              }
            })
        .addLast(
            new ChannelInboundHandlerAdapter() {
              @Override
              public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                ClientContexts.fail(ctx.channel(), instrumenter, cause);
              }
            });
    try {
      testing.runWithSpan(
          "first",
          () -> {
            parents.add(Span.current().getSpanContext());
            channel.writeOutbound(request("/first"));
          });
      testing.runWithSpan(
          "second",
          () -> {
            parents.add(Span.current().getSpanContext());
            channel.writeOutbound(request("/second"));
          });
      response(channel, 201);

      assertThat(channel.isActive()).isTrue();
      testing.runWithSpan(
          "third",
          () -> {
            parents.add(Span.current().getSpanContext());
            DefaultFullHttpRequest subsequent = request("/third");
            channel.writeOutbound(subsequent);
            assertThat(subsequent.headers().contains("traceparent")).isTrue();
          });
      response(channel, 202);
      response(channel, 203);

      assertThat(callbackParents).containsExactly(parents.get(0), parents.get(1), parents.get(2));
      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("first"),
                  span ->
                      span.hasName("GET")
                          .hasParent(trace.getSpan(0))
                          .hasAttributesSatisfying(
                              attributes ->
                                  assertThat(attributes.get(HTTP_RESPONSE_STATUS_CODE))
                                      .isEqualTo(201L))),
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("second"),
                  span ->
                      span.hasName("GET")
                          .hasParent(trace.getSpan(0))
                          .hasAttributesSatisfying(
                              attributes ->
                                  assertThat(attributes.get(HTTP_RESPONSE_STATUS_CODE))
                                      .isEqualTo(202L))),
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("third"),
                  span ->
                      span.hasName("GET")
                          .hasParent(trace.getSpan(0))
                          .hasAttributesSatisfying(
                              attributes ->
                                  assertThat(attributes.get(HTTP_RESPONSE_STATUS_CODE))
                                      .isEqualTo(203L))));
    } finally {
      channel.finishAndReleaseAll();
    }
  }

  @Test
  void nestedReadFailureDuringCompletedResponseCallbackStillFailsPendingRequest() {
    Instrumenter<NettyCommonRequest, HttpResponse> instrumenter =
        new NettyClientInstrumenterFactory(
                NettyClientInstrumenterBuilderFactory.create(
                    "io.opentelemetry.netty-4.1", testing.getOpenTelemetry()),
                NettyConnectionInstrumentationFlag.DISABLED,
                NettyConnectionInstrumentationFlag.DISABLED)
            .instrumenter();
    EmbeddedChannel channel = channel(new ChannelOutboundHandlerAdapter());
    Object nestedMessage = new Object();
    IOException failure = new IOException("nested read failed");
    channel
        .pipeline()
        .addLast(
            new ChannelInboundHandlerAdapter() {
              @Override
              public void channelRead(ChannelHandlerContext ctx, Object msg) {
                int previousDepth = ClientContexts.enterChannelRead(ctx.channel(), msg);
                try {
                  if (msg == nestedMessage) {
                    ReferenceCountUtil.release(msg);
                    ctx.fireExceptionCaught(failure);
                  } else if (msg instanceof HttpResponse) {
                    ctx.pipeline().fireChannelRead(nestedMessage);
                    ReferenceCountUtil.release(msg);
                  } else {
                    ReferenceCountUtil.release(msg);
                  }
                } finally {
                  ClientContexts.exitChannelRead(previousDepth);
                }
              }
            })
        .addLast(
            new ChannelInboundHandlerAdapter() {
              @Override
              public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                ClientContexts.fail(ctx.channel(), instrumenter, cause);
              }
            });
    try {
      testing.runWithSpan("first", () -> channel.writeOutbound(request("/first")));
      testing.runWithSpan("second", () -> channel.writeOutbound(request("/second")));
      response(channel, 201);

      DefaultFullHttpRequest subsequent = request("/subsequent");
      channel.writeOutbound(subsequent);
      assertThat(subsequent.headers().contains("traceparent")).isFalse();
      response(channel, 202);
      assertThat(channel.isActive()).isTrue();
      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("first"),
                  span ->
                      span.hasName("GET")
                          .hasParent(trace.getSpan(0))
                          .hasAttributesSatisfying(
                              attributes ->
                                  assertThat(attributes.get(HTTP_RESPONSE_STATUS_CODE))
                                      .isEqualTo(201L))),
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("second"),
                  span -> span.hasName("GET").hasParent(trace.getSpan(0))));
      assertThat(testing.spans()).hasSize(4);
      assertThat(testing.spans().get(3).getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
      assertThat(testing.spans().get(3).getEvents())
          .singleElement()
          .satisfies(
              event ->
                  assertThat(event.getAttributes().get(EXCEPTION_TYPE))
                      .isEqualTo(IOException.class.getName()));
    } finally {
      channel.finishAndReleaseAll();
    }
  }

  @Test
  void synchronousWriteFailureDisablesCorrelation() {
    EmbeddedChannel channel =
        channel(
            new ChannelOutboundHandlerAdapter() {
              private boolean first = true;

              @Override
              public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise)
                  throws Exception {
                if (first) {
                  first = false;
                  ReferenceCountUtil.release(msg);
                  throw new IOException("failed write");
                }
                super.write(ctx, msg, promise);
              }
            });
    try {
      assertThat(channel.writeAndFlush(request("/failed")).isSuccess()).isFalse();
      DefaultFullHttpRequest subsequent = request("/success");
      channel.writeOutbound(subsequent);
      assertThat(subsequent.headers().contains("traceparent")).isFalse();
      response(channel, 202);
      assertThat(channel.isActive()).isTrue();
      channel.close();
      assertThat(testing.spans()).hasSize(1);
      assertThat(testing.spans().get(0).getEvents()).hasSize(1);
      assertThat(testing.spans().get(0).getAttributes().get(HTTP_RESPONSE_STATUS_CODE)).isNull();
    } finally {
      channel.finishAndReleaseAll();
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void failedRequestContentDisablesCorrelation(boolean voidPromise) {
    EmbeddedChannel channel =
        channel(
            new ChannelOutboundHandlerAdapter() {
              @Override
              public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise)
                  throws Exception {
                if (msg instanceof HttpContent) {
                  ReferenceCountUtil.release(msg);
                  promise.setFailure(new IOException("content failed"));
                } else {
                  super.write(ctx, msg, promise);
                }
              }
            });
    channel
        .pipeline()
        .addLast(
            new ChannelInboundHandlerAdapter() {
              @Override
              public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {}
            });
    try {
      HttpRequest request = new DefaultHttpRequest(HTTP_1_1, HttpMethod.POST, "/first");
      request.headers().set("host", "localhost");
      channel.writeOutbound(request);
      if (voidPromise) {
        channel.writeAndFlush(new DefaultLastHttpContent(), channel.voidPromise());
      } else {
        assertThat(channel.writeAndFlush(new DefaultLastHttpContent()).isSuccess()).isFalse();
      }
      response(channel, 201);
      assertThat(channel.isActive()).isTrue();
      assertThat(testing.spans()).hasSize(1);
      assertThat(testing.spans().get(0).getEvents()).hasSize(1);
      assertThat(testing.spans().get(0).getAttributes().get(HTTP_RESPONSE_STATUS_CODE)).isNull();
    } finally {
      channel.finishAndReleaseAll();
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void unrelatedWriteFailureDoesNotDisableCorrelation(boolean requestPending) {
    Object unrelated = new Object();
    EmbeddedChannel channel =
        channel(
            new ChannelOutboundHandlerAdapter() {
              @Override
              public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise)
                  throws Exception {
                if (msg == unrelated) {
                  promise.setFailure(new IOException("unrelated write failed"));
                } else {
                  super.write(ctx, msg, promise);
                }
              }
            });
    try {
      if (requestPending) {
        testing.runWithSpan("first", () -> channel.writeOutbound(request("/first")));
      }
      assertThat(channel.writeAndFlush(unrelated).isSuccess()).isFalse();
      testing.runWithSpan(
          requestPending ? "second" : "first",
          () -> {
            DefaultFullHttpRequest request = request("/success");
            channel.writeOutbound(request);
            assertThat(request.headers().contains("traceparent")).isTrue();
          });
      if (requestPending) {
        response(channel, 201);
      }
      response(channel, 202);
      if (requestPending) {
        testing.waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("first"),
                    span ->
                        span.hasName("GET")
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfying(
                                attributes ->
                                    assertThat(attributes.get(HTTP_RESPONSE_STATUS_CODE))
                                        .isEqualTo(201L))),
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("second"),
                    span ->
                        span.hasName("GET")
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfying(
                                attributes ->
                                    assertThat(attributes.get(HTTP_RESPONSE_STATUS_CODE))
                                        .isEqualTo(202L))));
      } else {
        testing.waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("first"),
                    span ->
                        span.hasName("GET")
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfying(
                                attributes ->
                                    assertThat(attributes.get(HTTP_RESPONSE_STATUS_CODE))
                                        .isEqualTo(202L))));
        assertThat(testing.spans()).allSatisfy(span -> assertThat(span.getEvents()).isEmpty());
      }
    } finally {
      channel.finishAndReleaseAll();
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void upgradedRequestTransfersToHttp2Stream(boolean parentClosedBeforeRead) {
    EmbeddedChannel parent = channel(new ChannelOutboundHandlerAdapter());
    EmbeddedChannel stream =
        new EmbeddedChannel(
            NettyClientTelemetry.builder(testing.getOpenTelemetry())
                .build()
                .createCombinedHandler()) {
          @Override
          public Channel parent() {
            return parent;
          }
        };
    try {
      parent.writeOutbound(request("/upgrade"));
      Context pendingParent = Context.root().with(Span.getInvalid());
      if (!parentClosedBeforeRead) {
        NettyClientTelemetry.setParentContext(parent, pendingParent);
      }
      response(parent, 101);
      if (parentClosedBeforeRead) {
        parent.close();
      }
      ClientContexts.transferUpgradedRequest(parent, stream);
      if (!parentClosedBeforeRead) {
        assertThat(parent.attr(AttributeKeys.CLIENT_PARENT_CONTEXT).get()).isSameAs(pendingParent);
      }
      response(stream, 103);
      if (parentClosedBeforeRead) {
        assertThat(stream.attr(AttributeKeys.CLIENT_CONTEXT).get()).isNull();
      } else {
        assertThat(stream.attr(AttributeKeys.CLIENT_CONTEXT).get()).isNotNull();
      }
      assertThat(stream.attr(AttributeKeys.CLIENT_PARENT_CONTEXT).get()).isNull();
      parent.close();
      if (parentClosedBeforeRead) {
        assertThat(testing.spans()).hasSize(1);
        assertThat(stream.attr(AttributeKeys.CLIENT_CONTEXT).get()).isNull();
      } else {
        assertThat(testing.spans()).isEmpty();
      }
      response(stream, 200);
      assertThat(testing.spans()).hasSize(1);
      assertThat(testing.spans().get(0).getAttributes().get(HTTP_RESPONSE_STATUS_CODE))
          .isEqualTo(parentClosedBeforeRead ? null : 200L);
    } finally {
      stream.finishAndReleaseAll();
      parent.finishAndReleaseAll();
    }
  }

  @ParameterizedTest
  @CsvSource({"false, false", "false, true", "true, false", "true, true"})
  void upgradedRequestEndsWithErrorWhenStreamCloses(
      boolean fullUpgradeResponse, boolean partialResponse) {
    EmbeddedChannel parent = channel(new ChannelOutboundHandlerAdapter());
    EmbeddedChannel stream =
        new EmbeddedChannel(
            NettyClientTelemetry.builder(testing.getOpenTelemetry())
                .build()
                .createCombinedHandler()) {
          @Override
          public Channel parent() {
            return parent;
          }
        };
    try {
      parent.writeOutbound(request("/upgrade"));
      switchingProtocolsResponse(parent, fullUpgradeResponse);
      ClientContexts.transferUpgradedRequest(parent, stream);

      if (partialResponse) {
        stream.writeInbound(new DefaultHttpResponse(HTTP_1_1, HttpResponseStatus.OK));
        stream.writeInbound(new DefaultHttpContent(Unpooled.copiedBuffer("partial", UTF_8)));
      }
      stream.pipeline().fireChannelInactive();
      parent.close();

      assertThat(testing.spans()).hasSize(1);
      assertThat(testing.spans().get(0).getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
      assertThat(testing.spans().get(0).getEvents())
          .singleElement()
          .satisfies(
              event ->
                  assertThat(event.getAttributes().get(EXCEPTION_TYPE))
                      .isEqualTo(ClosedChannelException.class.getName()));
      assertThat(testing.spans().get(0).getAttributes().get(HTTP_RESPONSE_STATUS_CODE))
          .isEqualTo(partialResponse ? 200L : null);
      assertThat(parent.attr(AttributeKeys.CLIENT_CONTEXT).get()).isNull();
      assertThat(stream.attr(AttributeKeys.CLIENT_CONTEXT).get()).isNull();
    } finally {
      stream.finishAndReleaseAll();
      parent.finishAndReleaseAll();
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void downstreamExceptionBeforeFirstStreamReadEndsTransferredRequest(boolean fullUpgradeResponse) {
    Instrumenter<NettyCommonRequest, HttpResponse> instrumenter =
        new NettyClientInstrumenterFactory(
                NettyClientInstrumenterBuilderFactory.create(
                    "io.opentelemetry.netty-4.1", testing.getOpenTelemetry()),
                NettyConnectionInstrumentationFlag.DISABLED,
                NettyConnectionInstrumentationFlag.DISABLED)
            .instrumenter();
    NettyClientHandlerFactory handlerFactory = new NettyClientHandlerFactory(instrumenter, false);
    EmbeddedChannel parent = new EmbeddedChannel(handlerFactory.createCombinedHandler());
    EmbeddedChannel stream =
        new EmbeddedChannel() {
          @Override
          public Channel parent() {
            return parent;
          }
        };
    try {
      parent.writeOutbound(request("/upgrade"));
      switchingProtocolsResponse(parent, fullUpgradeResponse);
      ClientContexts.transferUpgradedRequest(parent, stream);
      AttributeKey<ClientContexts> key =
          AttributeKey.valueOf(ClientContexts.class, "client-contexts");
      assertThat(stream.hasAttr(key)).isTrue();

      IOException failure = new IOException("downstream handler failed");
      ClientContexts.fail(stream, instrumenter, failure);
      parent.close();

      assertThat(testing.spans()).hasSize(1);
      assertThat(testing.spans().get(0).getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
      assertThat(testing.spans().get(0).getEvents())
          .singleElement()
          .satisfies(
              event ->
                  assertThat(event.getAttributes().get(EXCEPTION_TYPE))
                      .isEqualTo(IOException.class.getName()));
      assertThat(testing.spans().get(0).getAttributes().get(HTTP_RESPONSE_STATUS_CODE)).isNull();
      assertThat(parent.attr(AttributeKeys.CLIENT_CONTEXT).get()).isNull();
      assertThat(stream.attr(AttributeKeys.CLIENT_CONTEXT).get()).isNull();
      assertThat(stream.attr(AttributeKeys.CLIENT_PARENT_CONTEXT).get()).isNull();
    } finally {
      stream.finishAndReleaseAll();
      parent.finishAndReleaseAll();
    }
  }

  private static DefaultFullHttpRequest request(String uri) {
    DefaultFullHttpRequest request = new DefaultFullHttpRequest(HTTP_1_1, HttpMethod.GET, uri);
    request.headers().set("host", "localhost");
    return request;
  }

  private static void response(EmbeddedChannel channel, int status) {
    channel.writeInbound(new DefaultFullHttpResponse(HTTP_1_1, HttpResponseStatus.valueOf(status)));
  }

  private static void switchingProtocolsResponse(EmbeddedChannel channel, boolean fullResponse) {
    if (fullResponse) {
      response(channel, 101);
    } else {
      channel.writeInbound(
          new DefaultHttpResponse(HTTP_1_1, HttpResponseStatus.SWITCHING_PROTOCOLS));
      channel.writeInbound(LastHttpContent.EMPTY_LAST_CONTENT);
    }
  }
}
