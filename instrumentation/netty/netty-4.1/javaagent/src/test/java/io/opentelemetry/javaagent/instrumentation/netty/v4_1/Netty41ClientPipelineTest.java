/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
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
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.netty.v4_1.ClientHandler;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestServer;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
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
  static InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @BeforeAll
  static void setUp() {
    server = new HttpClientTestServer(testing.getOpenTelemetry());
    server.start();
  }

  @AfterAll
  static void tearDown() {
    server.stop();
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
    assumeTrue(Boolean.getBoolean("testLatestDeps"));
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

  static class TracedClass {
    private final Bootstrap bootstrap;

    private TracedClass() {
      EventLoopGroup group = new NioEventLoopGroup();
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
            return result.get(20, TimeUnit.SECONDS);
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
