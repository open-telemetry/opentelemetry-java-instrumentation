/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.thrift.v0_13;

import static io.opentelemetry.instrumentation.testing.util.TestLatestDeps.testLatestDeps;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import custom.CustomService;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.thrift.v0_13.AbstractThriftTest;
import io.opentelemetry.instrumentation.thrift.v0_13.CustomAsyncHandler;
import io.opentelemetry.sdk.testing.assertj.TraceAssert;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.apache.thrift.TApplicationException;
import org.apache.thrift.TProcessor;
import org.apache.thrift.async.AsyncMethodCallback;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class ThriftTest extends AbstractThriftTest {
  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Override
  protected InstrumentationExtension getTesting() {
    return testing;
  }

  @Override
  protected TProcessor configure(TProcessor processor, String serviceName) {
    return processor;
  }

  @Override
  protected TProtocol configure(TProtocol protocol) {
    return protocol;
  }

  @Override
  protected TProtocolFactory configure(TProtocolFactory protocolFactory) {
    return protocolFactory;
  }

  @Override
  protected CustomService.AsyncIface configure(CustomService.AsyncClient asyncClient) {
    return asyncClient;
  }

  @Override
  protected CustomService.Iface configure(CustomService.Client client) {
    return client;
  }

  @Override
  protected boolean hasAsyncServerNetworkAttributes() {
    return true;
  }

  @Test
  void async() throws Exception {
    // with thrift 0.13 fails on Java 8 due to java.lang.NoSuchMethodError:
    // java.nio.ByteBuffer.rewind()Ljava/nio/ByteBuffer;
    assumeTrue(!"1.8".equals(System.getProperty("java.specification.version")) || testLatestDeps());

    int port = startAsyncServer();
    CustomService.AsyncIface asyncClient = createAsyncClient(port);

    CompletableFuture<String> completableFuture = new CompletableFuture<>();
    getTesting()
        .runWithSpan(
            "parent",
            () ->
                asyncClient.say(
                    "Async",
                    "World",
                    new AsyncMethodCallback<String>() {
                      @Override
                      public void onComplete(String response) {
                        getTesting()
                            .runWithSpan("callback", () -> completableFuture.complete(response));
                      }

                      @Override
                      public void onError(Exception exception) {
                        completableFuture.completeExceptionally(exception);
                      }
                    }));

    assertThat(completableFuture.get(15, SECONDS)).isEqualTo("Say Async World");

    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                    span -> assertClientSpan(span, "say", port).hasParent(trace.getSpan(0)),
                    span ->
                        assertServerSpan(
                                span, CustomAsyncHandler.class.getName(), "say", port, null)
                            .hasParent(trace.getSpan(1)),
                    span ->
                        span.hasName("callback")
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(0))));
  }

  @Test
  void asyncMany() throws Exception {
    // with thrift 0.13 fails on Java 8 due to java.lang.NoSuchMethodError:
    // java.nio.ByteBuffer.rewind()Ljava/nio/ByteBuffer;
    assumeTrue(!"1.8".equals(System.getProperty("java.specification.version")) || testLatestDeps());

    int port = startAsyncServer();

    List<CompletableFuture<String>> results = new ArrayList<>();
    for (int i = 0; i < 4; i++) {
      CustomService.AsyncIface asyncClient = createAsyncClient(port);
      CompletableFuture<String> completableFuture = new CompletableFuture<>();
      results.add(completableFuture);
      getTesting()
          .runWithSpan(
              "parent",
              () ->
                  asyncClient.withDelay(
                      1,
                      new AsyncMethodCallback<String>() {
                        @Override
                        public void onComplete(String response) {
                          getTesting()
                              .runWithSpan("callback", () -> completableFuture.complete(response));
                        }

                        @Override
                        public void onError(Exception exception) {
                          completableFuture.completeExceptionally(exception);
                        }
                      }));
    }
    for (CompletableFuture<String> completableFuture : results) {
      assertThat(completableFuture.get(15, SECONDS)).isEqualTo("delay 1");
    }

    Consumer<TraceAssert> traceAssert =
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span -> assertClientSpan(span, "withDelay", port).hasParent(trace.getSpan(0)),
                span ->
                    assertServerSpan(
                            span, CustomAsyncHandler.class.getName(), "withDelay", port, null)
                        .hasParent(trace.getSpan(1)),
                span ->
                    span.hasName("callback")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0)));
    getTesting().waitAndAssertTraces(traceAssert, traceAssert, traceAssert, traceAssert);
  }

  @Test
  void oneWayAsync() throws Exception {
    int port = startAsyncServer();
    CustomService.AsyncIface asyncClient = createAsyncClient(port);

    CompletableFuture<String> completableFuture = new CompletableFuture<>();
    getTesting()
        .runWithSpan(
            "parent",
            () ->
                asyncClient.oneWay(
                    new AsyncMethodCallback<Void>() {
                      @Override
                      public void onComplete(Void response) {
                        getTesting()
                            .runWithSpan("callback", () -> completableFuture.complete("ok"));
                      }

                      @Override
                      public void onError(Exception exception) {
                        completableFuture.completeExceptionally(exception);
                      }
                    }));

    assertThat(completableFuture.get(15, SECONDS)).isEqualTo("ok");

    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                    span -> assertClientSpan(span, "oneWay", port).hasParent(trace.getSpan(0)),
                    span ->
                        assertServerSpan(
                                span, CustomAsyncHandler.class.getName(), "oneWay", port, null)
                            .hasParent(trace.getSpan(1)),
                    span ->
                        span.hasName("callback")
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(0))));
  }

  @Test
  void asyncWithError() throws Exception {
    // with thrift 0.13 fails on Java 8 due to java.lang.NoSuchMethodError:
    // java.nio.ByteBuffer.rewind()Ljava/nio/ByteBuffer;
    assumeTrue(!"1.8".equals(System.getProperty("java.specification.version")) || testLatestDeps());

    int port = startAsyncServer();
    CustomService.AsyncIface asyncClient = createAsyncClient(port);

    CompletableFuture<String> completableFuture = new CompletableFuture<>();
    getTesting()
        .runWithSpan(
            "parent",
            () ->
                asyncClient.withError(
                    new AsyncMethodCallback<String>() {
                      @Override
                      public void onComplete(String response) {
                        completableFuture.complete(response);
                      }

                      @Override
                      public void onError(Exception exception) {
                        getTesting()
                            .runWithSpan(
                                "callback",
                                () -> completableFuture.completeExceptionally(exception));
                      }
                    }));

    assertThatThrownBy(() -> completableFuture.get(15, SECONDS))
        .hasRootCauseInstanceOf(TApplicationException.class);

    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                    span ->
                        assertClientSpan(span, "withError", port, true)
                            .hasParent(trace.getSpan(0))
                            .hasStatus(StatusData.error()),
                    span ->
                        assertServerSpan(
                                span,
                                CustomAsyncHandler.class.getName(),
                                "withError",
                                port,
                                IllegalStateException.class.getName())
                            .hasParent(trace.getSpan(1)),
                    span ->
                        span.hasName("callback")
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(0))));
  }
}
