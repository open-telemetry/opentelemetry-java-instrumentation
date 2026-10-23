/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_0.client;

import static io.opentelemetry.semconv.HttpAttributes.HTTP_RESPONSE_STATUS_CODE;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.assertj.core.api.Assertions.assertThat;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequestEncoder;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseDecoder;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.ReferenceCountUtil;
import io.opentelemetry.api.impl.InstrumentationUtil;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import test.netty.v4_0.WriteListener;

class Netty40PipeliningTest {
  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  void pipelinedResponsesKeepTheirOwnParents() {
    List<SpanContext> callbacks = new ArrayList<>();
    EmbeddedChannel channel = channel(callbacks);
    List<SpanContext> parents = new ArrayList<>();
    try {
      write(channel, "first", parents);
      write(channel, "second", parents);
      respond(channel, "HTTP/1.1 201 Created\r\nTransfer-Encoding: chunked\r\n\r\n1\r\no\r\n");
      respond(channel, "2\r\nne\r\n0\r\n\r\n");
      respond(channel, "HTTP/1.1 202 Accepted\r\nTransfer-Encoding: chunked\r\n\r\n1\r\nt\r\n");
      respond(channel, "2\r\nwo\r\n0\r\n\r\n");
      assertThat(callbacks)
          .containsExactly(
              parents.get(0),
              parents.get(0),
              parents.get(0),
              parents.get(0),
              parents.get(1),
              parents.get(1),
              parents.get(1),
              parents.get(1));
      assertResponses(201, 202);
    } finally {
      close(channel);
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {100, 103})
  void informationalResponseDoesNotConsumeRequest(int status) {
    List<SpanContext> callbacks = new ArrayList<>();
    // The original HttpClientCodec only recognizes 100 as interim in its own method queue.
    EmbeddedChannel channel =
        new EmbeddedChannel(
            new HttpRequestEncoder(), new HttpResponseDecoder(), callbacks(callbacks));
    List<SpanContext> parents = new ArrayList<>();
    try {
      write(channel, "first", parents);
      write(channel, "second", parents);
      respond(channel, "HTTP/1.1 " + status + " Informational\r\n\r\n");
      respond(channel, "HTTP/1.1 201 Created\r\nContent-Length: 0\r\n\r\n");
      respond(channel, "HTTP/1.1 202 Accepted\r\nContent-Length: 0\r\n\r\n");
      assertThat(callbacks)
          .containsExactly(
              parents.get(0),
              parents.get(0),
              parents.get(0),
              parents.get(0),
              parents.get(1),
              parents.get(1));
      assertResponses(201, 202);
    } finally {
      close(channel);
    }
  }

  @Test
  void suppressedRequestStillConsumesItsResponseWithoutInjectingHeaders() {
    EmbeddedChannel channel = channel(new ArrayList<>());
    List<SpanContext> parents = new ArrayList<>();
    try {
      DefaultFullHttpRequest suppressed = request();
      testing.runWithSpan(
          "suppressed",
          () ->
              InstrumentationUtil.suppressInstrumentation(() -> channel.writeAndFlush(suppressed)));
      assertThat(suppressed.headers().get("traceparent")).isNull();
      write(channel, "second", parents);
      respond(channel, "HTTP/1.1 203 Suppressed\r\nContent-Length: 0\r\n\r\n");
      respond(channel, "HTTP/1.1 202 Accepted\r\nContent-Length: 0\r\n\r\n");
      testing.waitAndAssertTraces(
          trace -> trace.hasSpansSatisfyingExactly(span -> span.hasName("suppressed")),
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("second"),
                  span ->
                      span.hasKind(SpanKind.CLIENT)
                          .hasParent(trace.getSpan(0))
                          .hasAttributesSatisfying(
                              attributes ->
                                  assertThat(attributes.get(HTTP_RESPONSE_STATUS_CODE))
                                      .isEqualTo(202L))));
    } finally {
      close(channel);
    }
  }

  @Test
  void switchingProtocolsIsTerminal() {
    EmbeddedChannel channel = channel(new ArrayList<>());
    try {
      write(channel, "upgrade", new ArrayList<>());
      respond(
          channel,
          "HTTP/1.1 101 Switching Protocols\r\nConnection: Upgrade\r\nUpgrade: test\r\n\r\n");
      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("upgrade"),
                  span ->
                      span.hasKind(SpanKind.CLIENT)
                          .hasParent(trace.getSpan(0))
                          .hasAttributesSatisfying(
                              attributes ->
                                  assertThat(attributes.get(HTTP_RESPONSE_STATUS_CODE))
                                      .isEqualTo(101L))));
    } finally {
      close(channel);
    }
  }

  @Test
  void suppressedRequestBetweenTracedRequestsPreservesFifoAndParentScopes() {
    List<SpanContext> callbacks = new ArrayList<>();
    List<SpanContext> parents = new ArrayList<>();
    EmbeddedChannel channel = channel(callbacks);
    try {
      write(channel, "first", parents);
      DefaultFullHttpRequest suppressed = request();
      testing.runWithSpan(
          "suppressed",
          () -> {
            parents.add(Span.current().getSpanContext());
            InstrumentationUtil.suppressInstrumentation(() -> channel.writeAndFlush(suppressed));
          });
      assertThat(suppressed.headers().get("traceparent")).isNull();
      write(channel, "second", parents);
      respond(channel, "HTTP/1.1 201 Created\r\nContent-Length: 0\r\n\r\n");
      respond(channel, "HTTP/1.1 203 Suppressed\r\nContent-Length: 0\r\n\r\n");
      respond(channel, "HTTP/1.1 202 Accepted\r\nContent-Length: 0\r\n\r\n");
      assertThat(callbacks)
          .containsExactly(
              parents.get(0), parents.get(0),
              parents.get(1), parents.get(1),
              parents.get(2), parents.get(2));
      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("first"),
                  span ->
                      span.hasKind(SpanKind.CLIENT)
                          .hasParent(trace.getSpan(0))
                          .hasAttributesSatisfying(
                              attributes ->
                                  assertThat(attributes.get(HTTP_RESPONSE_STATUS_CODE))
                                      .isEqualTo(201L))),
          trace -> trace.hasSpansSatisfyingExactly(span -> span.hasName("suppressed")),
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("second"),
                  span ->
                      span.hasKind(SpanKind.CLIENT)
                          .hasParent(trace.getSpan(0))
                          .hasAttributesSatisfying(
                              attributes ->
                                  assertThat(attributes.get(HTTP_RESPONSE_STATUS_CODE))
                                      .isEqualTo(202L))));
    } finally {
      close(channel);
    }
  }

  @Test
  void closeEndsAllPendingRequestsOnce() {
    EmbeddedChannel channel = channel(new ArrayList<>());
    write(channel, "first", new ArrayList<>());
    write(channel, "second", new ArrayList<>());
    close(channel);
    assertFailures();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void pipelineFailureEndsAllPendingRequestsOnce(boolean synchronousWriteFailure) {
    EmbeddedChannel channel = channel(new ArrayList<>());
    try {
      write(channel, "first", new ArrayList<>());
      if (synchronousWriteFailure) {
        channel
            .pipeline()
            .addFirst(
                "failed-write",
                new ChannelOutboundHandlerAdapter() {
                  @Override
                  public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise)
                      throws Exception {
                    ReferenceCountUtil.release(msg);
                    throw new IOException("synchronous write");
                  }
                });
      }
      write(channel, "second", new ArrayList<>());
      if (!synchronousWriteFailure) {
        channel.pipeline().fireExceptionCaught(new IOException("read failure"));
      }
      close(channel);
      assertFailures();
    } finally {
      close(channel);
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void failedWriteAbandonsAmbiguousResponsePairing(boolean voidPromise) {
    EmbeddedChannel channel = channel(new ArrayList<>());
    List<ChannelPromise> promises = new ArrayList<>();
    channel
        .pipeline()
        .addFirst(
            "deferred-write",
            new ChannelOutboundHandlerAdapter() {
              @Override
              public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
                ReferenceCountUtil.release(msg);
                promises.add(promise);
              }
            });
    try {
      write(channel, "first", new ArrayList<>());
      testing.runWithSpan(
          "second",
          () -> {
            if (voidPromise) {
              channel.writeAndFlush(request(), channel.voidPromise());
            } else {
              channel.writeAndFlush(request());
            }
          });
      promises.get(1).setFailure(new IOException("partial write"));
      assertThat(channel.isOpen()).isTrue();
      DefaultFullHttpRequest afterFailure = request();
      channel.writeAndFlush(afterFailure);
      assertThat(afterFailure.headers().get("traceparent")).isNull();
      promises.get(2).setSuccess();
      promises.get(0).setSuccess();
      respond(channel, "HTTP/1.1 201 Ambiguous\r\nContent-Length: 0\r\n\r\n");
      channel.close();
      assertFailures();
    } finally {
      close(channel);
    }
  }

  @Test
  void asynchronousWriteListenersRetainTheirOwnParents() {
    EmbeddedChannel channel = channel(new ArrayList<>());
    List<ChannelPromise> promises = new ArrayList<>();
    List<SpanContext> parents = new ArrayList<>();
    List<SpanContext> listeners = new ArrayList<>();
    channel
        .pipeline()
        .addFirst(
            "deferred-write",
            new ChannelOutboundHandlerAdapter() {
              @Override
              public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
                ReferenceCountUtil.release(msg);
                promises.add(promise);
              }
            });
    try {
      for (String name : new String[] {"first", "second"}) {
        testing.runWithSpan(
            name,
            () -> {
              parents.add(Span.current().getSpanContext());
              channel.writeAndFlush(request()).addListener(WriteListener.capture(listeners));
            });
      }
      promises.get(1).setSuccess();
      promises.get(0).setSuccess();
      assertThat(listeners).containsExactly(parents.get(1), parents.get(0));
      respond(channel, "HTTP/1.1 201 Created\r\nContent-Length: 0\r\n\r\n");
      respond(channel, "HTTP/1.1 202 Accepted\r\nContent-Length: 0\r\n\r\n");
      assertResponses(201, 202);
    } finally {
      close(channel);
    }
  }

  @Test
  void failedBodyWriteEndsRequestWithoutExceptionOrClose() {
    EmbeddedChannel channel = channel(new ArrayList<>());
    List<ChannelPromise> bodyPromises = new ArrayList<>();
    AtomicBoolean deferBody = new AtomicBoolean();
    channel
        .pipeline()
        .addFirst(
            "deferred-body",
            new ChannelOutboundHandlerAdapter() {
              @Override
              public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise)
                  throws Exception {
                if (deferBody.get()) {
                  ReferenceCountUtil.release(msg);
                  bodyPromises.add(promise);
                } else {
                  super.write(ctx, msg, promise);
                }
              }
            });
    try {
      testing.runWithSpan(
          "body",
          () -> {
            DefaultHttpRequest request =
                new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/");
            HttpHeaders.setHost(request, "localhost");
            HttpHeaders.setContentLength(request, 1);
            assertThat(channel.writeAndFlush(request).isSuccess()).isTrue();
            deferBody.set(true);
            channel.writeAndFlush(new DefaultLastHttpContent(Unpooled.copiedBuffer("x", US_ASCII)));
          });
      IOException failure = new IOException("body write");
      bodyPromises.get(0).setFailure(failure);
      assertThat(channel.isOpen()).isTrue();
      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("body"),
                  span ->
                      span.hasKind(SpanKind.CLIENT)
                          .hasParent(trace.getSpan(0))
                          .hasException(failure)));
    } finally {
      close(channel);
    }
  }

  private static EmbeddedChannel channel(List<SpanContext> callbacks) {
    return new EmbeddedChannel(new HttpClientCodec(), callbacks(callbacks));
  }

  private static ChannelInboundHandlerAdapter callbacks(List<SpanContext> callbacks) {
    return new ChannelInboundHandlerAdapter() {
      @Override
      public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof HttpResponse || msg instanceof HttpContent) {
          callbacks.add(Span.current().getSpanContext());
        }
        ReferenceCountUtil.release(msg);
      }

      @Override
      public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {}
    };
  }

  private static void write(EmbeddedChannel channel, String name, List<SpanContext> parents) {
    testing.runWithSpan(
        name,
        () -> {
          SpanContext parent = Span.current().getSpanContext();
          parents.add(parent);
          channel
              .writeAndFlush(request())
              .addListener(
                  (ChannelFutureListener)
                      future -> assertThat(Span.current().getSpanContext()).isEqualTo(parent));
        });
  }

  private static DefaultFullHttpRequest request() {
    DefaultFullHttpRequest request =
        new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/");
    HttpHeaders.setHost(request, "localhost");
    return request;
  }

  private static void respond(EmbeddedChannel channel, String response) {
    channel.writeInbound(Unpooled.copiedBuffer(response, US_ASCII));
  }

  private static void assertResponses(int firstStatus, int secondStatus) {
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("first"),
                span ->
                    span.hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfying(
                            attributes ->
                                assertThat(attributes.get(HTTP_RESPONSE_STATUS_CODE))
                                    .isEqualTo((long) firstStatus))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("second"),
                span ->
                    span.hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfying(
                            attributes ->
                                assertThat(attributes.get(HTTP_RESPONSE_STATUS_CODE))
                                    .isEqualTo((long) secondStatus))));
  }

  private static void assertFailures() {
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("first"),
                span ->
                    span.hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .satisfies(
                            data ->
                                assertThat(data.getAttributes().get(HTTP_RESPONSE_STATUS_CODE))
                                    .isNull())),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("second"),
                span ->
                    span.hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .satisfies(
                            data ->
                                assertThat(data.getAttributes().get(HTTP_RESPONSE_STATUS_CODE))
                                    .isNull())));
  }

  private static void close(EmbeddedChannel channel) {
    channel.finish();
    Object message;
    while ((message = channel.readOutbound()) != null) {
      ReferenceCountUtil.release(message);
    }
    while ((message = channel.readInbound()) != null) {
      ReferenceCountUtil.release(message);
    }
  }
}
