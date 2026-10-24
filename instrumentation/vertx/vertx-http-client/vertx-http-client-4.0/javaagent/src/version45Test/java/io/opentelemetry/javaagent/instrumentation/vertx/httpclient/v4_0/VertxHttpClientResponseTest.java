/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.httpclient.v4_0;

import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.api.trace.SpanKind.INTERNAL;
import static io.opentelemetry.api.trace.SpanKind.SERVER;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.instrumentation.test.utils.PortUtils;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.web.Router;
import io.vertx.grpc.client.GrpcClient;
import io.vertx.grpc.common.ServiceName;
import io.vertx.grpc.server.GrpcServer;
import io.vertx.grpc.server.GrpcServerRequest;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Socket;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class VertxHttpClientResponseTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static final ServiceName SERVICE_NAME = ServiceName.create("echo", "Echo");

  private final Vertx vertx = Vertx.vertx();
  private final int grpcPort = PortUtils.findOpenPort();
  private final Map<String, GrpcServerRequest<Buffer, Buffer>> concurrentRequests = new HashMap<>();

  private GrpcClient grpcClient;

  @BeforeEach
  void startGrpcServer() throws Exception {
    grpcClient =
        GrpcClient.client(
            vertx,
            new HttpClientOptions()
                .setMaxPoolSize(1)
                .setProtocolVersion(HttpVersion.HTTP_2)
                .setHttp2ClearTextUpgrade(false));

    GrpcServer grpcServer = GrpcServer.server(vertx);
    grpcServer.callHandler(
        request ->
            request.handler(
                message -> {
                  String text = message.toString();
                  if (text.startsWith("concurrent-")) {
                    concurrentRequests.put(text, request);
                    if (concurrentRequests.size() == 2) {
                      for (Map.Entry<String, GrpcServerRequest<Buffer, Buffer>> entry :
                          concurrentRequests.entrySet()) {
                        entry.getValue().response().end(Buffer.buffer(entry.getKey()));
                      }
                      concurrentRequests.clear();
                    }
                  } else if (text.equals("abort")) {
                    request
                        .response()
                        .write(message)
                        .onSuccess(ignored -> request.connection().close());
                  } else {
                    request.response().end(message);
                  }
                }));

    vertx
        .createHttpServer()
        .requestHandler(grpcServer)
        .listen(grpcPort, "localhost")
        .toCompletionStage()
        .toCompletableFuture()
        .get(30, SECONDS);
  }

  @AfterEach
  void closeVertx() {
    vertx.close();
  }

  @Test
  void grpcResponseCallbacksRetainContextAndContinueRouting() throws Exception {
    CompletableFuture<SpanContext> incomingContext = new CompletableFuture<>();
    CompletableFuture<SpanContext> bodyContext = new CompletableFuture<>();
    CompletableFuture<SpanContext> endContext = new CompletableFuture<>();
    CompletableFuture<SpanContext> nextRouteContext = new CompletableFuture<>();
    CompletableFuture<SpanContext> secondBodyContext = new CompletableFuture<>();
    CompletableFuture<SpanContext> secondEndContext = new CompletableFuture<>();

    Router router = Router.router(vertx);
    router
        .route("/test")
        .handler(
            routingContext -> {
              routingContext.request().pause();
              incomingContext.complete(Span.current().getSpanContext());
              sendCall(
                  "first",
                  response -> {
                    bodyContext.complete(Span.current().getSpanContext());
                    routingContext.request().resume();
                    routingContext.next();
                    sendCall(
                        "second",
                        ignored -> secondBodyContext.complete(Span.current().getSpanContext()),
                        secondBodyContext::completeExceptionally,
                        ignored -> secondEndContext.complete(Span.current().getSpanContext()));
                  },
                  bodyContext::completeExceptionally,
                  ignored -> endContext.complete(Span.current().getSpanContext()));
            });
    router
        .route("/test")
        .handler(
            routingContext -> {
              nextRouteContext.complete(Span.current().getSpanContext());
              routingContext.response().end("done");
            });

    int routerPort = PortUtils.findOpenPort();
    vertx
        .createHttpServer()
        .requestHandler(router)
        .listen(routerPort, "localhost")
        .toCompletionStage()
        .toCompletableFuture()
        .get(30, SECONDS);

    try (Socket socket = new Socket("localhost", routerPort)) {
      socket
          .getOutputStream()
          .write(
              "GET /test HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n"
                  .getBytes(US_ASCII));
      socket.getOutputStream().flush();
      assertThat(
              new BufferedReader(new InputStreamReader(socket.getInputStream(), UTF_8)).readLine())
          .contains("200");
    }

    SpanContext parent = incomingContext.get(30, SECONDS);
    assertThat(parent.isValid()).isTrue();
    assertThat(bodyContext.get(30, SECONDS).getTraceId()).isEqualTo(parent.getTraceId());
    assertThat(endContext.get(30, SECONDS).getTraceId()).isEqualTo(parent.getTraceId());
    assertThat(nextRouteContext.get(30, SECONDS).getTraceId()).isEqualTo(parent.getTraceId());
    assertThat(secondBodyContext.get(30, SECONDS).getTraceId()).isEqualTo(parent.getTraceId());
    assertThat(secondEndContext.get(30, SECONDS).getTraceId()).isEqualTo(parent.getTraceId());

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasKind(SERVER).hasNoParent(),
                span -> span.hasKind(CLIENT).hasParent(trace.getSpan(0)),
                span -> span.hasKind(CLIENT).hasParent(trace.getSpan(0))));
  }

  @Test
  void concurrentHttp2ResponsesKeepTheirOwnParentContexts() throws Exception {
    CompletableFuture<SpanContext> firstResponseContext = new CompletableFuture<>();
    CompletableFuture<SpanContext> secondResponseContext = new CompletableFuture<>();

    SpanContext firstParent =
        testing.runWithSpan(
            "parent-one",
            () -> {
              SpanContext context = Span.current().getSpanContext();
              sendCall(
                  "concurrent-one",
                  ignored -> firstResponseContext.complete(Span.current().getSpanContext()),
                  firstResponseContext::completeExceptionally,
                  ignored -> {});
              return context;
            });
    SpanContext secondParent =
        testing.runWithSpan(
            "parent-two",
            () -> {
              SpanContext context = Span.current().getSpanContext();
              sendCall(
                  "concurrent-two",
                  ignored -> secondResponseContext.complete(Span.current().getSpanContext()),
                  secondResponseContext::completeExceptionally,
                  ignored -> {});
              return context;
            });

    assertThat(firstParent.getTraceId()).isNotEqualTo(secondParent.getTraceId());
    assertThat(firstResponseContext.get(30, SECONDS).getTraceId())
        .isEqualTo(firstParent.getTraceId());
    assertThat(secondResponseContext.get(30, SECONDS).getTraceId())
        .isEqualTo(secondParent.getTraceId());

    testing.waitAndAssertSortedTraces(
        Comparator.comparing(trace -> trace.get(0).getName()),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent-one").hasKind(INTERNAL).hasNoParent(),
                span -> span.hasKind(CLIENT).hasParent(trace.getSpan(0))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent-two").hasKind(INTERNAL).hasNoParent(),
                span -> span.hasKind(CLIENT).hasParent(trace.getSpan(0))));
  }

  @Test
  void responseFailureHandlerRetainsCallerContext() throws Exception {
    CompletableFuture<SpanContext> bodyContext = new CompletableFuture<>();
    CompletableFuture<SpanContext> failureContext = new CompletableFuture<>();

    SpanContext parent =
        testing.runWithSpan(
            "parent",
            () -> {
              SpanContext context = Span.current().getSpanContext();
              sendCall(
                  "abort",
                  ignored -> bodyContext.complete(Span.current().getSpanContext()),
                  ignored -> failureContext.complete(Span.current().getSpanContext()),
                  ignored -> {});
              return context;
            });

    assertThat(bodyContext.get(30, SECONDS).getTraceId()).isEqualTo(parent.getTraceId());
    assertThat(failureContext.get(30, SECONDS).getTraceId()).isEqualTo(parent.getTraceId());

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(INTERNAL).hasNoParent(),
                span -> span.hasKind(CLIENT).hasParent(trace.getSpan(0))));
  }

  private void sendCall(
      String message,
      Handler<Buffer> bodyHandler,
      Handler<Throwable> failureHandler,
      Handler<Void> endHandler) {
    SocketAddress address = SocketAddress.inetSocketAddress(grpcPort, "localhost");
    grpcClient
        .request(address)
        .onFailure(failureHandler)
        .onSuccess(
            request -> {
              request.serviceName(SERVICE_NAME);
              request.methodName("Exchange");
              request.end(Buffer.buffer(message));
              request
                  .response()
                  .onFailure(failureHandler)
                  .onSuccess(
                      response -> {
                        response.exceptionHandler(failureHandler);
                        response.endHandler(endHandler);
                        response.handler(bodyHandler);
                      });
            });
  }
}
