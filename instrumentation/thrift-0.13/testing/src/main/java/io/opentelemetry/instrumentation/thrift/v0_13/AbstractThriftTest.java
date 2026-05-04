/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

// Includes work from:
/*
 * Thrift Opentracing Component
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.instrumentation.thrift.v0_13;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldRpcSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableRpcSemconv;
import static io.opentelemetry.instrumentation.testing.util.TestLatestDeps.testLatestDeps;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_LOCAL_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_LOCAL_PORT;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_TYPE;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_METHOD;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SERVICE;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SYSTEM;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SYSTEM_NAME;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.api.Named.named;

import custom.Address;
import custom.CustomService;
import custom.User;
import custom.UserWithAddress;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.testing.assertj.TraceAssert;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.apache.thrift.TApplicationException;
import org.apache.thrift.TProcessor;
import org.apache.thrift.TProcessorFactory;
import org.apache.thrift.async.AsyncMethodCallback;
import org.apache.thrift.async.TAsyncClientManager;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.server.TNonblockingServer;
import org.apache.thrift.server.TServer;
import org.apache.thrift.server.TSimpleServer;
import org.apache.thrift.server.TThreadPoolServer;
import org.apache.thrift.transport.TNonblockingServerSocket;
import org.apache.thrift.transport.TNonblockingSocket;
import org.apache.thrift.transport.TNonblockingTransport;
import org.apache.thrift.transport.TServerSocket;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportFactory;
import org.assertj.core.api.AbstractLongAssert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

public abstract class AbstractThriftTest {

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  protected abstract InstrumentationExtension getTesting();

  protected abstract boolean hasAsyncServerNetworkAttributes();

  protected abstract TProcessor configure(TProcessor processor, String serviceName);

  protected abstract TProtocol configure(TProtocol protocol, String serviceName);

  protected abstract TProtocolFactory configure(
      TProtocolFactory protocolFactory, String serviceName, TTransport transport);

  protected abstract CustomService.AsyncIface configure(CustomService.AsyncClient asyncClient);

  protected int startSimpleServer() throws Exception {
    return startSimpleServer(true);
  }

  protected int startSimpleServer(boolean configure) throws Exception {
    return startSimpleServer(configure, null);
  }

  protected int startSimpleServer(TTransportFactory transportFactory) throws Exception {
    return startSimpleServer(true, transportFactory);
  }

  protected int startSimpleServer(boolean configure, TTransportFactory transportFactory)
      throws Exception {
    CustomHandler handler = new CustomHandler();
    TProcessor processor = new CustomService.Processor<CustomService.Iface>(handler);
    if (configure) {
      processor = configure(processor, CustomHandler.class.getName());
    }

    TServerSocket transport = new TServerSocket(0);
    TServer.Args serverArgs = new TServer.Args(transport).processor(processor);
    if (transportFactory != null) {
      serverArgs.transportFactory(transportFactory);
    }
    TServer server = new TSimpleServer(serverArgs);

    new Thread(server::serve).start();
    cleanup.deferCleanup(server::stop);

    return transport.getServerSocket().getLocalPort();
  }

  protected int startThreadPoolServer() throws Exception {
    return startThreadPoolServer(true);
  }

  protected int startThreadPoolServer(boolean configure) throws Exception {
    CustomHandler handler = new CustomHandler();
    TProcessor processor = new CustomService.Processor<CustomService.Iface>(handler);
    if (configure) {
      processor = configure(processor, CustomHandler.class.getName());
    }

    TServerSocket transport = new TServerSocket(0);
    TProtocolFactory protocolFactory = new TBinaryProtocol.Factory();
    TThreadPoolServer.Args args =
        new TThreadPoolServer.Args(transport)
            .processorFactory(new TProcessorFactory(processor))
            .protocolFactory(protocolFactory)
            .minWorkerThreads(5)
            .maxWorkerThreads(10);
    TServer server = new TThreadPoolServer(args);

    new Thread(server::serve).start();
    cleanup.deferCleanup(server::stop);

    return transport.getServerSocket().getLocalPort();
  }

  protected int startAsyncServer() throws Exception {
    return startAsyncServer(true);
  }

  protected int startAsyncServer(boolean configure) throws Exception {
    CustomHandler handler = new CustomHandler();
    TProcessor processor = new CustomService.Processor<CustomService.Iface>(handler);
    if (configure) {
      processor = configure(processor, CustomHandler.class.getName());
    }

    TNonblockingServerSocket transport = new TNonblockingServerSocket(0, 30000);
    TNonblockingServer.Args tnbArgs = new TNonblockingServer.Args(transport);
    tnbArgs.processor(processor);

    TServer server = new TNonblockingServer(tnbArgs);
    new Thread(server::serve).start();
    cleanup.deferCleanup(server::stop);

    return transport.getPort();
  }

  protected int startServer(String serverKind) throws Exception {
    return startServer(serverKind, true);
  }

  protected int startServer(String serverKind, boolean configure) throws Exception {
    switch (serverKind) {
      case "simple":
        return startSimpleServer(configure);
      case "threadPool":
        return startThreadPoolServer(configure);
      default:
        throw new IllegalArgumentException("Unsupported server kind: " + serverKind);
    }
  }

  protected CustomService.Client createClient(int port) throws Exception {
    return createClient(port, true);
  }

  protected CustomService.Client createClient(int port, boolean configure) throws Exception {
    return createClient(port, configure, null);
  }

  protected CustomService.Client createClient(int port, TTransportFactory transportFactory)
      throws Exception {
    return createClient(port, true, transportFactory);
  }

  protected CustomService.Client createClient(
      int port, boolean configure, TTransportFactory transportFactory) throws Exception {
    TTransport transport = new TSocket("localhost", port);
    if (transportFactory != null) {
      transport = transportFactory.getTransport(transport);
    }
    transport.open();
    cleanup.deferCleanup(transport);
    TProtocol protocol = new TBinaryProtocol(transport);
    if (configure) {
      protocol = configure(protocol, CustomService.class.getName());
    }
    return new CustomService.Client(protocol);
  }

  protected CustomService.AsyncIface createAsyncClient(int port) throws Exception {
    TNonblockingTransport transport = new TNonblockingSocket("localhost", port);
    TProtocolFactory protocolFactory =
        configure(new TBinaryProtocol.Factory(), CustomService.class.getName(), transport);
    TAsyncClientManager clientManager = new TAsyncClientManager();
    return configure(new CustomService.AsyncClient(protocolFactory, clientManager, transport));
  }

  protected SpanDataAssert assertClientSpan(SpanDataAssert span, String method, int port) {
    return assertClientSpan(span, method, port, true);
  }

  @SuppressWarnings({"deprecation"}) // using deprecated semconv
  protected SpanDataAssert assertClientSpan(
      SpanDataAssert span, String method, long port, boolean hasServerAttributes) {
    return span.hasName(CustomService.class.getName() + "/" + method)
        .hasKind(SpanKind.CLIENT)
        .hasAttributesSatisfyingExactly(
            equalTo(
                RPC_METHOD,
                emitStableRpcSemconv() ? CustomService.class.getName() + "/" + method : method),
            equalTo(RPC_SERVICE, emitOldRpcSemconv() ? CustomService.class.getName() : null),
            equalTo(RPC_SYSTEM, emitOldRpcSemconv() ? "apache_thrift" : null),
            equalTo(RPC_SYSTEM_NAME, emitStableRpcSemconv() ? "apache_thrift" : null),
            equalTo(SERVER_PORT, hasServerAttributes ? port : null),
            equalTo(SERVER_ADDRESS, hasServerAttributes ? "localhost" : null),
            equalTo(NETWORK_TYPE, "ipv4"),
            equalTo(NETWORK_PEER_ADDRESS, "127.0.0.1"),
            equalTo(NETWORK_PEER_PORT, port));
  }

  @SuppressWarnings("deprecation") // using deprecated semconv
  protected SpanDataAssert assertServerSpan(SpanDataAssert span, String method, int port) {
    return span.hasName(CustomHandler.class.getName() + "/" + method)
        .hasKind(SpanKind.SERVER)
        .hasAttributesSatisfyingExactly(
            equalTo(
                RPC_METHOD,
                emitStableRpcSemconv() ? CustomHandler.class.getName() + "/" + method : method),
            equalTo(RPC_SERVICE, emitOldRpcSemconv() ? CustomHandler.class.getName() : null),
            equalTo(RPC_SYSTEM, emitOldRpcSemconv() ? "apache_thrift" : null),
            equalTo(RPC_SYSTEM_NAME, emitStableRpcSemconv() ? "apache_thrift" : null),
            equalTo(SERVER_PORT, port),
            equalTo(SERVER_ADDRESS, "127.0.0.1"),
            equalTo(NETWORK_TYPE, "ipv4"),
            equalTo(NETWORK_PEER_ADDRESS, "127.0.0.1"),
            satisfies(NETWORK_PEER_PORT, AbstractLongAssert::isNotNegative),
            equalTo(NETWORK_LOCAL_ADDRESS, "127.0.0.1"),
            equalTo(NETWORK_LOCAL_PORT, port));
  }

  @SuppressWarnings("deprecation") // using deprecated semconv
  protected SpanDataAssert assertServerSpan(SpanDataAssert span, String method) {
    return span.hasName(CustomHandler.class.getName() + "/" + method)
        .hasKind(SpanKind.SERVER)
        .hasAttributesSatisfyingExactly(
            equalTo(
                RPC_METHOD,
                emitStableRpcSemconv() ? CustomHandler.class.getName() + "/" + method : method),
            equalTo(RPC_SERVICE, emitOldRpcSemconv() ? CustomHandler.class.getName() : null),
            equalTo(RPC_SYSTEM, emitOldRpcSemconv() ? "apache_thrift" : null),
            equalTo(RPC_SYSTEM_NAME, emitStableRpcSemconv() ? "apache_thrift" : null));
  }

  @SuppressWarnings("deprecation") // using deprecated semconv
  private void assertMetrics(int serverPort) {
    if (emitOldRpcSemconv()) {
      getTesting()
          .waitAndAssertMetrics(
              "io.opentelemetry.thrift-0.13",
              metric ->
                  metric
                      .hasName("rpc.server.duration")
                      .hasUnit("ms")
                      .hasHistogramSatisfying(
                          histogram ->
                              histogram.hasPointsSatisfying(
                                  point ->
                                      point.hasAttributesSatisfyingExactly(
                                          equalTo(SERVER_ADDRESS, "127.0.0.1"),
                                          equalTo(SERVER_PORT, serverPort),
                                          equalTo(RPC_METHOD, "say"),
                                          equalTo(RPC_SERVICE, CustomHandler.class.getName()),
                                          equalTo(RPC_SYSTEM, "apache_thrift"),
                                          equalTo(NETWORK_TYPE, "ipv4")))));
      getTesting()
          .waitAndAssertMetrics(
              "io.opentelemetry.thrift-0.13",
              metric ->
                  metric
                      .hasName("rpc.client.duration")
                      .hasUnit("ms")
                      .hasHistogramSatisfying(
                          histogram ->
                              histogram.hasPointsSatisfying(
                                  point ->
                                      point.hasAttributesSatisfyingExactly(
                                          equalTo(SERVER_ADDRESS, "localhost"),
                                          equalTo(SERVER_PORT, serverPort),
                                          equalTo(RPC_METHOD, "say"),
                                          equalTo(RPC_SERVICE, CustomService.class.getName()),
                                          equalTo(RPC_SYSTEM, "apache_thrift"),
                                          equalTo(NETWORK_TYPE, "ipv4")))));
    }
    if (emitStableRpcSemconv()) {
      getTesting()
          .waitAndAssertMetrics(
              "io.opentelemetry.thrift-0.13",
              metric ->
                  metric
                      .hasName("rpc.server.call.duration")
                      .hasUnit("s")
                      .hasHistogramSatisfying(
                          histogram ->
                              histogram.hasPointsSatisfying(
                                  point ->
                                      point.hasAttributesSatisfyingExactly(
                                          equalTo(RPC_SYSTEM_NAME, "apache_thrift"),
                                          equalTo(SERVER_ADDRESS, "127.0.0.1"),
                                          equalTo(SERVER_PORT, serverPort),
                                          equalTo(
                                              RPC_METHOD,
                                              CustomHandler.class.getName() + "/say")))));
      getTesting()
          .waitAndAssertMetrics(
              "io.opentelemetry.thrift-0.13",
              metric ->
                  metric
                      .hasName("rpc.client.call.duration")
                      .hasUnit("s")
                      .hasHistogramSatisfying(
                          histogram ->
                              histogram.hasPointsSatisfying(
                                  point ->
                                      point.hasAttributesSatisfyingExactly(
                                          equalTo(RPC_SYSTEM_NAME, "apache_thrift"),
                                          equalTo(SERVER_ADDRESS, "localhost"),
                                          equalTo(SERVER_PORT, serverPort),
                                          equalTo(
                                              RPC_METHOD,
                                              CustomService.class.getName() + "/say")))));
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"simple", "threadPool"})
  void instrumentedClientAndServer(String serverKind) throws Exception {
    int port = startServer(serverKind);
    CustomService.Client client = createClient(port);

    getTesting()
        .runWithSpan(
            "parent", () -> assertThat(client.say("Hello", "World")).isEqualTo("Say Hello World"));

    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                    span -> assertClientSpan(span, "say", port).hasParent(trace.getSpan(0)),
                    span -> assertServerSpan(span, "say", port).hasParent(trace.getSpan(1))));

    assertMetrics(port);
  }

  private static Stream<Arguments> transports() throws Exception {
    Class<?> framedTransportClass;
    try {
      framedTransportClass = Class.forName("org.apache.thrift.transport.TFramedTransport$Factory");
    } catch (ClassNotFoundException e) {
      framedTransportClass =
          Class.forName("org.apache.thrift.transport.layered.TFramedTransport$Factory");
    }
    Class<?> fastFramedTransportClass;
    try {
      fastFramedTransportClass =
          Class.forName("org.apache.thrift.transport.TFastFramedTransport$Factory");
    } catch (ClassNotFoundException e) {
      fastFramedTransportClass =
          Class.forName("org.apache.thrift.transport.layered.TFastFramedTransport$Factory");
    }
    TTransportFactory framedTransportFactory =
        (TTransportFactory) framedTransportClass.getConstructor().newInstance();
    TTransportFactory fastFramedTransportFactory =
        (TTransportFactory) fastFramedTransportClass.getConstructor().newInstance();
    return Stream.of(
        Arguments.of(named("framed", framedTransportFactory)),
        Arguments.of(named("fast framed", fastFramedTransportFactory)));
  }

  @ParameterizedTest
  @MethodSource(value = "transports")
  void transportFactory(TTransportFactory transportFactory) throws Exception {
    int port = startSimpleServer(transportFactory);
    CustomService.Client client = createClient(port, transportFactory);

    getTesting()
        .runWithSpan(
            "parent", () -> assertThat(client.say("Hello", "World")).isEqualTo("Say Hello World"));

    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                    span -> assertClientSpan(span, "say", port).hasParent(trace.getSpan(0)),
                    span -> assertServerSpan(span, "say", port).hasParent(trace.getSpan(1))));

    assertMetrics(port);
  }

  @Test
  void withoutArgs() throws Exception {
    int port = startSimpleServer();
    CustomService.Client client = createClient(port);

    assertThat(client.withoutArgs()).isEqualTo("no args");

    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> assertClientSpan(span, "withoutArgs", port).hasNoParent(),
                    span ->
                        assertServerSpan(span, "withoutArgs", port).hasParent(trace.getSpan(0))));
  }

  @Test
  void withError() throws Exception {
    int port = startSimpleServer();
    CustomService.Client client = createClient(port);

    assertThatThrownBy(client::withError).isInstanceOf(TApplicationException.class);

    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        assertClientSpan(span, "withError", port)
                            .hasNoParent()
                            .hasStatus(StatusData.error()),
                    span ->
                        assertServerSpan(span, "withError", port)
                            .hasParent(trace.getSpan(0))
                            .hasStatus(StatusData.error())));
  }

  @Test
  void withCollision() throws Exception {
    int port = startSimpleServer();
    CustomService.Client client = createClient(port);

    assertThat(client.withCollision("collision")).isEqualTo("collision");

    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> assertClientSpan(span, "withCollision", port).hasNoParent(),
                    span ->
                        assertServerSpan(span, "withCollision", port).hasParent(trace.getSpan(0))));
  }

  @Test
  void oneWayWithError() throws Exception {
    int port = startSimpleServer();
    CustomService.Client client = createClient(port);

    client.oneWayWithError();

    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        assertClientSpan(span, "oneWayWithError", port)
                            .hasNoParent()
                            // client span is not aware of the error
                            .hasStatus(StatusData.unset()),
                    span ->
                        assertServerSpan(span, "oneWayWithError", port)
                            .hasParent(trace.getSpan(0))
                            // currently we detect the error only when the response is sent
                            .hasStatus(StatusData.unset())));
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
                    span -> assertClientSpan(span, "say", port, false).hasParent(trace.getSpan(0)),
                    span ->
                        (hasAsyncServerNetworkAttributes()
                                ? assertServerSpan(span, "say", port)
                                : assertServerSpan(span, "say"))
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
                span ->
                    assertClientSpan(span, "withDelay", port, false).hasParent(trace.getSpan(0)),
                span ->
                    (hasAsyncServerNetworkAttributes()
                            ? assertServerSpan(span, "withDelay", port)
                            : assertServerSpan(span, "withDelay"))
                        .hasParent(trace.getSpan(1)),
                span ->
                    span.hasName("callback")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0)));
    getTesting().waitAndAssertTraces(traceAssert, traceAssert, traceAssert, traceAssert);
  }

  @Test
  void oneWay() throws Exception {
    int port = startSimpleServer();
    CustomService.Client client = createClient(port);

    client.oneWay();

    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> assertClientSpan(span, "oneWay", port).hasNoParent(),
                    span -> assertServerSpan(span, "oneWay", port).hasParent(trace.getSpan(0))));
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
                    span ->
                        assertClientSpan(span, "oneWay", port, false).hasParent(trace.getSpan(0)),
                    span ->
                        (hasAsyncServerNetworkAttributes()
                                ? assertServerSpan(span, "oneWay", port)
                                : assertServerSpan(span, "oneWay"))
                            .hasParent(trace.getSpan(1)),
                    span ->
                        span.hasName("callback")
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(0))));
  }

  @Test
  void withStruct() throws Exception {
    int port = startSimpleServer();
    CustomService.Client client = createClient(port);

    User user = new User("name32", 30);
    Address address = new Address("line", "City", "1234AB");

    UserWithAddress userWithAddress = client.save(user, address);

    assertThat(userWithAddress.user).isEqualTo(user);
    assertThat(userWithAddress.address).isEqualTo(address);

    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> assertClientSpan(span, "save", port).hasNoParent(),
                    span -> assertServerSpan(span, "save", port).hasParent(trace.getSpan(0))));
  }

  @Test
  void manyCalls() throws Exception {
    int port = startSimpleServer();
    CustomService.Client client = createClient(port);

    assertThat(client.say("one", "two")).isEqualTo("Say one two");
    assertThat(client.say("three", "four")).isEqualTo("Say three four");
    client.oneWay();
    assertThat(client.withoutArgs()).isEqualTo("no args");

    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> assertClientSpan(span, "say", port).hasNoParent(),
                    span -> assertServerSpan(span, "say", port).hasParent(trace.getSpan(0))),
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> assertClientSpan(span, "say", port).hasNoParent(),
                    span -> assertServerSpan(span, "say", port).hasParent(trace.getSpan(0))),
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> assertClientSpan(span, "oneWay", port).hasNoParent(),
                    span -> assertServerSpan(span, "oneWay", port).hasParent(trace.getSpan(0))),
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> assertClientSpan(span, "withoutArgs", port).hasNoParent(),
                    span ->
                        assertServerSpan(span, "withoutArgs", port).hasParent(trace.getSpan(0))));
  }

  @Test
  void manyCallsParallel() throws Exception {
    int port = startThreadPoolServer();
    ExecutorService executor = Executors.newFixedThreadPool(4);
    cleanup.deferCleanup(executor::shutdownNow);

    List<CompletableFuture<String>> results = new ArrayList<>();
    for (int i = 0; i < 4; i++) {
      results.add(
          CompletableFuture.supplyAsync(
              () -> {
                try {
                  CustomService.Client client = createClient(port);
                  return getTesting().runWithSpan("parent", () -> client.withDelay(1));
                } catch (Exception e) {
                  throw new IllegalStateException(e);
                }
              },
              executor));
    }

    for (CompletableFuture<String> completableFuture : results) {
      assertThat(completableFuture.get(15, SECONDS)).isEqualTo("delay 1");
    }

    Consumer<TraceAssert> traceAssert =
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span -> assertClientSpan(span, "withDelay", port).hasParent(trace.getSpan(0)),
                span -> assertServerSpan(span, "withDelay", port).hasParent(trace.getSpan(1)));
    getTesting().waitAndAssertTraces(traceAssert, traceAssert, traceAssert, traceAssert);
  }
}
