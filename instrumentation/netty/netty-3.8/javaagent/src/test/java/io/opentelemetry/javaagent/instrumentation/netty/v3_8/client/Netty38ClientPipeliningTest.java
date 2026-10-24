/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v3_8.client;

import static io.opentelemetry.semconv.HttpAttributes.HTTP_RESPONSE_STATUS_CODE;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.test.utils.PortUtils;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelDownstreamHandler;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.handler.codec.http.DefaultHttpChunk;
import org.jboss.netty.handler.codec.http.DefaultHttpRequest;
import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.HttpClientCodec;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class Netty38ClientPipeliningTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private final List<SpanContext> callbacks = new ArrayList<>();
  private final List<SpanContext> bodyCallbacks = new ArrayList<>();
  private final CountDownLatch responses = new CountDownLatch(2);
  private final AtomicReference<Runnable> responseAction = new AtomicReference<>();
  private ServerSocket server;
  private Socket peer;
  private BufferedReader requests;
  private ClientBootstrap bootstrap;
  private Channel channel;

  @BeforeEach
  void setUp() throws Exception {
    server = new ServerSocket(PortUtils.findOpenPort());
    bootstrap =
        new ClientBootstrap(
            new NioClientSocketChannelFactory(
                Executors.newCachedThreadPool(), Executors.newCachedThreadPool(), 1));
    bootstrap.setPipelineFactory(
        () ->
            Channels.pipeline(
                new HttpClientCodec(),
                new SimpleChannelUpstreamHandler() {
                  @Override
                  public void messageReceived(ChannelHandlerContext ctx, MessageEvent event) {
                    if (event.getMessage() instanceof HttpResponse) {
                      int status = ((HttpResponse) event.getMessage()).getStatus().getCode();
                      if (status >= 200 || status == 101) {
                        callbacks.add(Span.current().getSpanContext());
                        responses.countDown();
                        Runnable action = responseAction.getAndSet(null);
                        if (action != null) {
                          action.run();
                        }
                      }
                    } else if (event.getMessage() instanceof HttpChunk) {
                      bodyCallbacks.add(Span.current().getSpanContext());
                    }
                  }
                }));
    channel =
        bootstrap
            .connect(new InetSocketAddress("localhost", server.getLocalPort()))
            .syncUninterruptibly()
            .getChannel();
    peer = server.accept();
    peer.setSoTimeout(10_000);
    requests = new BufferedReader(new InputStreamReader(peer.getInputStream(), US_ASCII));
  }

  @AfterEach
  void tearDown() throws Exception {
    if (channel != null) {
      channel.close().syncUninterruptibly();
    }
    if (peer != null) {
      peer.close();
    }
    if (server != null) {
      server.close();
    }
    if (bootstrap != null) {
      bootstrap.releaseExternalResources();
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void correlatesPipelinedRequestsAndInformationalResponses(boolean chunked) throws Exception {
    SpanContext[] parents = new SpanContext[2];
    testing.runWithSpan(
        "first",
        () -> {
          parents[0] = Span.current().getSpanContext();
          write("/first");
        });
    testing.runWithSpan(
        "second",
        () -> {
          parents[1] = Span.current().getSpanContext();
          write("/second");
        });
    readRequest();
    readRequest();
    assertThat(testing.spans())
        .extracting(SpanData::getKind)
        .containsExactly(SpanKind.INTERNAL, SpanKind.INTERNAL);

    respond(
        "HTTP/1.1 100 Continue\r\n\r\n"
            + "HTTP/1.1 200 OK\r\n"
            + (chunked
                ? "Transfer-Encoding: chunked\r\n\r\n3\r\none\r\n0\r\n\r\n"
                : "Content-Length: 0\r\n\r\n")
            + "HTTP/1.1 201 Created\r\nContent-Length: 0\r\n\r\n");

    assertThat(responses.await(10, SECONDS)).isTrue();
    assertThat(callbacks).containsExactly(parents);
    if (chunked) {
      assertThat(bodyCallbacks).isNotEmpty().containsOnly(parents[0]);
    } else {
      assertThat(bodyCallbacks).isEmpty();
    }
    assertRequests(200, 201);
  }

  @Test
  void suppressedRequestDoesNotShiftResponses() throws Exception {
    testing.runWithSpan(
        "first", () -> testing.runWithHttpClientSpan("outer", () -> write("/suppressed")));
    testing.runWithSpan("second", () -> write("/traced"));
    readRequest();
    readRequest();
    respond(
        "HTTP/1.1 202 Accepted\r\nContent-Length: 0\r\n\r\n"
            + "HTTP/1.1 201 Created\r\nContent-Length: 0\r\n\r\n");
    assertThat(responses.await(10, SECONDS)).isTrue();
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("first"),
                span -> span.hasName("outer").hasParent(trace.getSpan(0))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("second"),
                span ->
                    span.hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasStatus(StatusData.unset())
                        .satisfies(
                            data ->
                                assertThat(data.getAttributes().get(HTTP_RESPONSE_STATUS_CODE))
                                    .isEqualTo(201))));
  }

  @Test
  void closeEndsAllPendingRequests() throws Exception {
    testing.runWithSpan("first", () -> write("/first"));
    testing.runWithSpan("second", () -> write("/second"));
    readRequest();
    readRequest();
    channel.close().syncUninterruptibly();
    assertRequests(null, null);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void failedWriteEndsPendingRequestsWithoutReassigningResponses(boolean body) throws Exception {
    testing.runWithSpan("first", () -> write("/first"));
    readRequest();
    if (body) {
      testing.runWithSpan(
          "second",
          () -> {
            DefaultHttpRequest request = request("/second");
            request.setChunked(true);
            channel.write(request).syncUninterruptibly();
          });
    }
    ChannelFuture[] failedWrite = new ChannelFuture[1];
    channel
        .getPipeline()
        .addFirst(
            "delay-write",
            new SimpleChannelDownstreamHandler() {
              @Override
              public void writeRequested(ChannelHandlerContext ctx, MessageEvent event) {
                failedWrite[0] = event.getFuture();
              }
            });
    if (body) {
      channel.write(new DefaultHttpChunk(ChannelBuffers.wrappedBuffer(new byte[] {1})));
    } else {
      testing.runWithSpan("second", () -> channel.write(request("/second")));
    }
    assertThat(failedWrite[0]).isNotNull();
    assertThat(failedWrite[0].isDone()).isFalse();
    failedWrite[0].setFailure(new IllegalStateException("write failed"));
    respond(
        "HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n"
            + "HTTP/1.1 201 Created\r\nContent-Length: 0\r\n\r\n");
    assertThat(responses.await(10, SECONDS)).isTrue();
    assertRequests(null, null);
  }

  @Test
  void failedControlWriteDoesNotFailPendingRequests() throws Exception {
    SpanContext[] parents = new SpanContext[2];
    channel
        .getPipeline()
        .addFirst(
            "fail-control-write",
            new SimpleChannelDownstreamHandler() {
              @Override
              public void writeRequested(ChannelHandlerContext ctx, MessageEvent event)
                  throws Exception {
                if ("control".equals(event.getMessage())) {
                  event.getFuture().setFailure(new IllegalStateException("control write failed"));
                } else {
                  super.writeRequested(ctx, event);
                }
              }
            });
    testing.runWithSpan(
        "first",
        () -> {
          parents[0] = Span.current().getSpanContext();
          write("/first");
        });
    readRequest();
    assertThat(channel.write("control").awaitUninterruptibly().isSuccess()).isFalse();
    testing.runWithSpan(
        "second",
        () -> {
          parents[1] = Span.current().getSpanContext();
          write("/second");
        });
    readRequest();
    respond(
        "HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n"
            + "HTTP/1.1 201 Created\r\nContent-Length: 0\r\n\r\n");
    assertThat(responses.await(10, SECONDS)).isTrue();
    assertThat(callbacks).containsExactly(parents);
    assertRequests(200, 201);
  }

  @Test
  void exceptionEndsAllPendingRequests() throws Exception {
    testing.runWithSpan("first", () -> write("/first"));
    testing.runWithSpan("second", () -> write("/second"));
    readRequest();
    readRequest();
    Channels.fireExceptionCaught(channel, new IllegalStateException("connection failed"));
    channel.close().syncUninterruptibly();
    assertRequests(null, null);
  }

  @Test
  void overlappingWritesDoNotReverseCorrelation() throws Exception {
    CountDownLatch entered = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    channel
        .getPipeline()
        .addFirst(
            "overlap",
            new SimpleChannelDownstreamHandler() {
              private final AtomicBoolean first = new AtomicBoolean(true);

              @Override
              public void writeRequested(ChannelHandlerContext ctx, MessageEvent event)
                  throws Exception {
                if (first.compareAndSet(true, false)) {
                  entered.countDown();
                  assertThat(release.await(10, SECONDS)).isTrue();
                }
                super.writeRequested(ctx, event);
              }
            });
    ExecutorService writer = Executors.newSingleThreadExecutor();
    try {
      Future<?> first = writer.submit(() -> testing.runWithSpan("first", () -> write("/first")));
      assertThat(entered.await(10, SECONDS)).isTrue();
      testing.runWithSpan("second", () -> write("/second"));
      release.countDown();
      first.get(10, SECONDS);
      readRequest();
      readRequest();
      respond(
          "HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n"
              + "HTTP/1.1 201 Created\r\nContent-Length: 0\r\n\r\n");
      assertThat(responses.await(10, SECONDS)).isTrue();
      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("first"),
                  span ->
                      span.hasKind(SpanKind.CLIENT)
                          .hasParent(trace.getSpan(0))
                          .hasStatus(StatusData.error())),
          trace -> trace.hasSpansSatisfyingExactly(span -> span.hasName("second")));
    } finally {
      release.countDown();
      writer.shutdownNow();
      assertThat(writer.awaitTermination(10, SECONDS)).isTrue();
    }
  }

  @Test
  void completedRequestAllowsSequentialWriteBeforePriorWriteReturns() throws Exception {
    CountDownLatch forwarded = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    CountDownLatch secondWrite = new CountDownLatch(1);
    SpanContext[] parents = new SpanContext[2];
    responseAction.set(
        () ->
            testing.runWithSpan(
                "second",
                () -> {
                  parents[1] = Span.current().getSpanContext();
                  channel.write(request("/second"));
                  secondWrite.countDown();
                }));
    channel
        .getPipeline()
        .addFirst(
            "delay-write-return",
            new SimpleChannelDownstreamHandler() {
              private final AtomicBoolean first = new AtomicBoolean(true);

              @Override
              public void writeRequested(ChannelHandlerContext ctx, MessageEvent event)
                  throws Exception {
                super.writeRequested(ctx, event);
                if (first.compareAndSet(true, false)) {
                  forwarded.countDown();
                  assertThat(release.await(10, SECONDS)).isTrue();
                }
              }
            });
    ExecutorService writer = Executors.newSingleThreadExecutor();
    Future<?> first =
        writer.submit(
            () ->
                testing.runWithSpan(
                    "first",
                    () -> {
                      parents[0] = Span.current().getSpanContext();
                      write("/first");
                    }));
    try {
      assertThat(forwarded.await(10, SECONDS)).isTrue();
      readRequest();
      respond("HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n");
      assertThat(secondWrite.await(10, SECONDS)).isTrue();
      readRequest();
      respond("HTTP/1.1 201 Created\r\nContent-Length: 0\r\n\r\n");
      assertThat(responses.await(10, SECONDS)).isTrue();
      release.countDown();
      first.get(10, SECONDS);
      assertThat(callbacks).containsExactly(parents);
      testing.waitForTraces(1);
      List<SpanData> clientSpans =
          testing.spans().stream()
              .filter(span -> span.getKind() == SpanKind.CLIENT)
              .collect(toList());
      assertThat(clientSpans).hasSize(2);
      assertThat(clientSpans)
          .extracting(SpanData::getParentSpanId)
          .containsExactly(parents[0].getSpanId(), parents[1].getSpanId());
      assertThat(clientSpans)
          .extracting(span -> span.getAttributes().get(HTTP_RESPONSE_STATUS_CODE))
          .containsExactly(200L, 201L);
    } finally {
      release.countDown();
      writer.shutdownNow();
      assertThat(writer.awaitTermination(10, SECONDS)).isTrue();
    }
  }

  private void write(String path) {
    channel.write(request(path)).syncUninterruptibly();
  }

  private DefaultHttpRequest request(String path) {
    DefaultHttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, path);
    request.headers().set("Host", "localhost:" + server.getLocalPort());
    return request;
  }

  private void readRequest() throws Exception {
    String line;
    do {
      line = requests.readLine();
      assertThat(line).isNotNull();
    } while (!line.isEmpty());
  }

  private void respond(String response) throws Exception {
    peer.getOutputStream().write(response.getBytes(US_ASCII));
    peer.getOutputStream().flush();
  }

  private static void assertRequests(Integer firstStatus, Integer secondStatus) {
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("first"),
                span ->
                    span.hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasStatus(firstStatus == null ? StatusData.error() : StatusData.unset())
                        .satisfies(
                            data ->
                                assertThat(data.getAttributes().get(HTTP_RESPONSE_STATUS_CODE))
                                    .isEqualTo(
                                        firstStatus == null ? null : firstStatus.longValue()))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("second"),
                span ->
                    span.hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasStatus(secondStatus == null ? StatusData.error() : StatusData.unset())
                        .satisfies(
                            data ->
                                assertThat(data.getAttributes().get(HTTP_RESPONSE_STATUS_CODE))
                                    .isEqualTo(
                                        secondStatus == null ? null : secondStatus.longValue()))));
  }
}
