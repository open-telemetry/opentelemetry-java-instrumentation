/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_1;

import static io.opentelemetry.instrumentation.testing.util.TestLatestDeps.testLatestDeps;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_RESPONSE_STATUS_CODE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.ReferenceCountUtil;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.netty.v4_1.ClientHandler;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestServer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class Netty41ClientPipelineTest {

  private static HttpClientTestServer server;

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @BeforeAll
  static void setUp() {
    server = new HttpClientTestServer(testing.getOpenTelemetry());
    server.start();
    cleanup.deferAfterAll(server::stop);
  }

  @Test
  @DisplayName("test connection reuse and second request with lazy execute")
  void testConnectionReuse() throws InterruptedException {
    // Create a simple Netty pipeline
    EventLoopGroup group = new NioEventLoopGroup();
    cleanup.deferCleanup(group::shutdownGracefully);
    Bootstrap b = new Bootstrap();
    b.group(group)
        .channel(NioSocketChannel.class)
        .handler(
            new ChannelInitializer<SocketChannel>() {
              @Override
              protected void initChannel(SocketChannel socketChannel) throws Exception {
                ChannelPipeline pipeline = socketChannel.pipeline();
                pipeline.addLast(new HttpClientCodec());
              }
            });
    DefaultFullHttpRequest request =
        new DefaultFullHttpRequest(
            HttpVersion.HTTP_1_1, HttpMethod.GET, "/success", Unpooled.EMPTY_BUFFER);
    request.headers().set(HttpHeaderNames.HOST, "localhost:" + server.httpPort());
    // note that this is a purely asynchronous request
    Channel ch =
        testing.runWithSpan(
            "parent1",
            () -> {
              Channel channel = b.connect("localhost", server.httpPort()).sync().channel();
              channel.write(request);
              channel.flush();
              return channel;
            });
    // This is a cheap/easy way to block/ensure that the first request has finished and check
    // reported spans midway through the complex sequence of events
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasNoParent().hasName("parent1").hasKind(SpanKind.INTERNAL),
                span -> span.hasParent(trace.getSpan(0)).hasKind(SpanKind.CLIENT),
                span -> span.hasKind(SpanKind.SERVER).hasParent(trace.getSpan(1))));

    testing.clearData();
    // now run a second request through the same channel
    testing.runWithSpan(
        "parent2",
        () -> {
          ch.write(request);
          ch.flush();
        });
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasNoParent().hasName("parent2").hasKind(SpanKind.INTERNAL),
                span -> span.hasKind(SpanKind.CLIENT).hasParent(trace.getSpan(0)),
                span -> span.hasKind(SpanKind.SERVER).hasParent(trace.getSpan(1))));
  }

  @Test
  void handledExceptionFromCompletedResponsePreservesPipelinedRequest() {
    EmbeddedChannel channel = new EmbeddedChannel();
    IOException failure = new IOException("response callback failed");
    List<Throwable> failures = new ArrayList<>();
    channel.pipeline().addLast(new HttpClientCodec());
    channel
        .pipeline()
        .addLast(
            new ChannelInboundHandlerAdapter() {
              private boolean firstResponse = true;

              @Override
              public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                boolean throwFailure = firstResponse && msg instanceof LastHttpContent;
                if (throwFailure) {
                  firstResponse = false;
                }
                ReferenceCountUtil.release(msg);
                if (throwFailure) {
                  throw failure;
                }
              }

              @Override
              public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                failures.add(cause);
              }
            });
    try {
      testing.runWithSpan("first", () -> channel.writeOutbound(request("/first")));
      testing.runWithSpan("second", () -> channel.writeOutbound(request("/second")));

      channel.writeInbound(
          Unpooled.copiedBuffer("HTTP/1.1 201 Created\r\nContent-Length: 0\r\n\r\n", UTF_8));
      assertThat(channel.isActive()).isTrue();
      channel.writeInbound(
          Unpooled.copiedBuffer("HTTP/1.1 202 Accepted\r\nContent-Length: 0\r\n\r\n", UTF_8));

      testing.runWithSpan(
          "third",
          () -> {
            DefaultFullHttpRequest subsequent = request("/third");
            channel.writeOutbound(subsequent);
            assertThat(subsequent.headers().contains("traceparent")).isTrue();
          });
      channel.writeInbound(
          Unpooled.copiedBuffer(
              "HTTP/1.1 203 Non-Authoritative Information\r\nContent-Length: 0\r\n\r\n", UTF_8));

      assertThat(failures).containsExactly(failure);
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
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("second"),
                  span ->
                      span.hasKind(SpanKind.CLIENT)
                          .hasParent(trace.getSpan(0))
                          .hasAttributesSatisfying(
                              attributes ->
                                  assertThat(attributes.get(HTTP_RESPONSE_STATUS_CODE))
                                      .isEqualTo(202L))),
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("third"),
                  span ->
                      span.hasKind(SpanKind.CLIENT)
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
  @DisplayName("when a handler is added to the netty pipeline we add our tracing handler")
  void testAddHandler() {
    EmbeddedChannel channel = new EmbeddedChannel();
    ChannelPipeline pipeline = channel.pipeline();

    pipeline.addLast("name", new HttpClientCodec());

    // The first one returns the removed tracing handler
    assertThat(
            pipeline.remove(
                "io.opentelemetry.javaagent.shaded.instrumentation.netty.v4_1.internal.client.HttpClientTracingHandler"))
        .isNotNull();
  }

  @Test
  @DisplayName("when a handler is added to the netty pipeline we add ONLY ONE tracing handler")
  void testAddHandlerOnlyOneTracingHandler() {
    EmbeddedChannel channel = new EmbeddedChannel();
    ChannelPipeline pipeline = channel.pipeline();

    pipeline.addLast("name", new HttpClientCodec());
    // The first one returns the removed tracing handler
    pipeline.remove(
        "io.opentelemetry.javaagent.shaded.instrumentation.netty.v4_1.internal.client.HttpClientTracingHandler");
    // There is only one
    assertThatThrownBy(
            () ->
                pipeline.remove(
                    "io.opentelemetry.javaagent.shaded.instrumentation.netty.v4_1.internal.client.HttpClientTracingHandler"))
        .isInstanceOf(NoSuchElementException.class);
  }

  @Test
  @DisplayName("handlers of different types can be added")
  void testHandlersOfDifferentTypesCanBeAdded() {
    EmbeddedChannel channel = new EmbeddedChannel();
    ChannelPipeline pipeline = channel.pipeline();

    pipeline.addLast("some_handler", new SimpleHandler());
    pipeline.addLast("a_traced_handler", new HttpClientCodec());

    // The first one returns the removed tracing handler
    assertThat(
            pipeline.remove(
                "io.opentelemetry.javaagent.shaded.instrumentation.netty.v4_1.internal.client.HttpClientTracingHandler"))
        .isNotNull();
    assertThat(pipeline.remove("some_handler")).isNotNull();
    assertThat(pipeline.remove("a_traced_handler")).isNotNull();
  }

  @Test
  @DisplayName(
      "calling pipeline.addLast methods that use overloaded methods does not cause infinite loop")
  void testCallAddLast() {
    EmbeddedChannel channel = new EmbeddedChannel();

    channel.pipeline().addLast(new SimpleHandler(), new OtherSimpleHandler());

    assertThat(channel.pipeline().remove("Netty41ClientPipelineTest$SimpleHandler#0")).isNotNull();
    assertThat(channel.pipeline().remove("Netty41ClientPipelineTest$OtherSimpleHandler#0"))
        .isNotNull();
  }

  @Test
  @DisplayName(
      "when a traced handler is added from an initializer we still detect it and add our channel handlers")
  void testAddInitializer() {
    // This test method replicates a scenario similar to how reactor 0.8.x register the
    // `HttpClientCodec` handler into the pipeline.
    assumeTrue(testLatestDeps());
    EmbeddedChannel channel = new EmbeddedChannel();

    channel.pipeline().addLast(new TracedHandlerFromInitializerHandler());

    assertThat(
            channel
                .pipeline()
                .get(
                    "io.opentelemetry.javaagent.shaded.instrumentation.netty.v4_1.internal.client.HttpClientTracingHandler"))
        .isNotNull();
    assertThat(channel.pipeline().remove("added_in_initializer")).isNotNull();
    assertThat(
            channel
                .pipeline()
                .get(
                    "io.opentelemetry.javaagent.shaded.instrumentation.netty.v4_1.internal.client.HttpClientTracingHandler"))
        .isNull();
  }

  @DisplayName("request with trace annotated method #method")
  @ParameterizedTest
  @ValueSource(strings = {"POST", "PUT"})
  void testRequestWithAnnotatedMethod(String method) throws Exception {
    TracedClass annotatedClass = new TracedClass();

    int responseCode = testing.runWithSpan("parent", () -> annotatedClass.tracedMethod(method));

    assertThat(responseCode).isEqualTo(200);
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasNoParent().hasName("parent").hasKind(SpanKind.INTERNAL),
                span -> span.hasParent(trace.getSpan(0)).hasName("tracedMethod"),
                span -> span.hasKind(SpanKind.CLIENT).hasParent(trace.getSpan(1)),
                span -> span.hasKind(SpanKind.SERVER).hasParent(trace.getSpan(2))));
  }

  private static DefaultFullHttpRequest request(String path) {
    DefaultFullHttpRequest request =
        new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, path);
    request.headers().set(HttpHeaderNames.HOST, "localhost");
    return request;
  }

  static class TracedClass {
    private final Bootstrap bootstrap;

    private TracedClass() {
      EventLoopGroup group = new NioEventLoopGroup();
      cleanup.deferCleanup(group::shutdownGracefully);
      bootstrap = new Bootstrap();
      bootstrap
          .group(group)
          .channel(NioSocketChannel.class)
          .handler(
              new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel socketChannel) throws Exception {
                  ChannelPipeline pipeline = socketChannel.pipeline();
                  pipeline.addLast(new HttpClientCodec());
                }
              });
    }

    int tracedMethod(String method) throws Exception {
      return testing.runWithSpan(
          "tracedMethod",
          () -> {
            DefaultFullHttpRequest request =
                new DefaultFullHttpRequest(
                    HttpVersion.HTTP_1_1,
                    HttpMethod.valueOf(method),
                    "/success",
                    Unpooled.EMPTY_BUFFER);
            request.headers().set(HttpHeaderNames.HOST, "localhost:" + server.httpPort());
            Channel ch = bootstrap.connect("localhost", server.httpPort()).sync().channel();
            CompletableFuture<Integer> result = new CompletableFuture<>();
            ch.pipeline().addLast(new ClientHandler(result));
            ch.writeAndFlush(request).get();
            return result.get(20, SECONDS);
          });
    }
  }

  static class SimpleHandler implements ChannelHandler {
    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {}

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {}

    @Override
    @Deprecated
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {}
  }

  static class OtherSimpleHandler implements ChannelHandler {
    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {}

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {}

    @Override
    @Deprecated
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {}
  }

  static class TracedHandlerFromInitializerHandler extends ChannelInitializer<Channel>
      implements ChannelHandler {
    @Override
    protected void initChannel(Channel ch) throws Exception {
      // This replicates how reactor 0.8.x add the HttpClientCodec
      ch.pipeline().addLast("added_in_initializer", new HttpClientCodec());
    }

    @Override
    @Deprecated
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {}
  }
}
