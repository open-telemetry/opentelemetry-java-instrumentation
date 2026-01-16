/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_6;

import static io.opentelemetry.instrumentation.grpc.v1_6.ExperimentalTestHelper.GRPC_RECEIVED_MESSAGE_COUNT;
import static io.opentelemetry.instrumentation.grpc.v1_6.ExperimentalTestHelper.GRPC_SENT_MESSAGE_COUNT;
import static io.opentelemetry.instrumentation.grpc.v1_6.ExperimentalTestHelper.experimentalSatisfies;
import static io.opentelemetry.instrumentation.testing.junit.rpc.RpcSemconvStabilityUtil.errorTypeAssertion;
import static io.opentelemetry.instrumentation.testing.junit.rpc.RpcSemconvStabilityUtil.getClientDurationMetricName;
import static io.opentelemetry.instrumentation.testing.junit.rpc.RpcSemconvStabilityUtil.getDurationUnit;
import static io.opentelemetry.instrumentation.testing.junit.rpc.RpcSemconvStabilityUtil.getServerDurationMetricName;
import static io.opentelemetry.instrumentation.testing.junit.rpc.RpcSemconvStabilityUtil.grpcStatusCodeAssertion;
import static io.opentelemetry.instrumentation.testing.junit.rpc.RpcSemconvStabilityUtil.rpcMethodAssertions;
import static io.opentelemetry.instrumentation.testing.junit.rpc.RpcSemconvStabilityUtil.rpcSystemAssertion;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_TYPE;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import example.GreeterGrpc;
import example.Helloworld;
import io.grpc.BindableService;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.protobuf.services.ProtoReflectionService;
import io.grpc.reflection.v1alpha.ServerReflectionGrpc;
import io.grpc.reflection.v1alpha.ServerReflectionRequest;
import io.grpc.reflection.v1alpha.ServerReflectionResponse;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.util.ThrowingRunnable;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.semconv.incubating.MessageIncubatingAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

@SuppressWarnings("deprecation") // using deprecated semconv
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractGrpcTest {
  protected static final String CLIENT_REQUEST_METADATA_KEY = "some-client-key";

  protected static final String SERVER_REQUEST_METADATA_KEY = "some-server-key";

  protected abstract ServerBuilder<?> configureServer(ServerBuilder<?> server);

  protected abstract ManagedChannelBuilder<?> configureClient(ManagedChannelBuilder<?> client);

  protected abstract InstrumentationExtension testing();

  protected final Queue<ThrowingRunnable<?>> closer = new ConcurrentLinkedQueue<>();

  @AfterEach
  void tearDown() throws Throwable {
    while (!closer.isEmpty()) {
      closer.poll().run();
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"some name", "some other name"})
  void successBlockingStub(String paramName) throws Exception {
    BindableService greeter =
        new GreeterGrpc.GreeterImplBase() {
          @Override
          public void sayHello(
              Helloworld.Request req, StreamObserver<Helloworld.Response> responseObserver) {
            Helloworld.Response reply =
                Helloworld.Response.newBuilder().setMessage("Hello " + req.getName()).build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
          }
        };
    Server server = configureServer(ServerBuilder.forPort(0).addService(greeter)).build().start();
    ManagedChannel channel = createChannel(server);
    closer.add(() -> channel.shutdownNow().awaitTermination(10, TimeUnit.SECONDS));
    closer.add(() -> server.shutdownNow().awaitTermination());

    GreeterGrpc.GreeterBlockingStub client = GreeterGrpc.newBlockingStub(channel);

    Helloworld.Response response =
        testing()
            .runWithSpan(
                "parent",
                () -> client.sayHello(Helloworld.Request.newBuilder().setName(paramName).build()));

    String prefix = "Hello ";
    assertThat(response.getMessage()).isEqualTo(prefix + paramName);

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                    span -> {
                      List<AttributeAssertion> attrs = new ArrayList<>();
                      attrs.add(
                          experimentalSatisfies(
                              GRPC_RECEIVED_MESSAGE_COUNT, v -> assertThat(v).isGreaterThan(0)));
                      attrs.add(
                          experimentalSatisfies(
                              GRPC_SENT_MESSAGE_COUNT, v -> assertThat(v).isGreaterThan(0)));
                      attrs.add(rpcSystemAssertion("grpc"));
                      attrs.addAll(rpcMethodAssertions("example.Greeter", "SayHello"));
                      attrs.add(grpcStatusCodeAssertion(Status.Code.OK.value()));
                      attrs.add(equalTo(SERVER_ADDRESS, "localhost"));
                      attrs.add(equalTo(SERVER_PORT, (long) server.getPort()));
                      span.hasName("example.Greeter/SayHello")
                          .hasKind(SpanKind.CLIENT)
                          .hasParent(trace.getSpan(0))
                          .hasAttributesSatisfyingExactly(
                              addExtraClientAttributes(attrs.toArray(new AttributeAssertion[0])))
                          .hasEventsSatisfyingExactly(
                              event ->
                                  event
                                      .hasName("message")
                                      .hasAttributesSatisfyingExactly(
                                          equalTo(MessageIncubatingAttributes.MESSAGE_TYPE, "SENT"),
                                          equalTo(MessageIncubatingAttributes.MESSAGE_ID, 1L)),
                              event ->
                                  event
                                      .hasName("message")
                                      .hasAttributesSatisfyingExactly(
                                          equalTo(
                                              MessageIncubatingAttributes.MESSAGE_TYPE, "RECEIVED"),
                                          equalTo(MessageIncubatingAttributes.MESSAGE_ID, 1L)));
                    },
                    span -> {
                      List<AttributeAssertion> attrs = new ArrayList<>();
                      attrs.add(
                          experimentalSatisfies(
                              GRPC_RECEIVED_MESSAGE_COUNT, v -> assertThat(v).isGreaterThan(0)));
                      attrs.add(
                          experimentalSatisfies(
                              GRPC_SENT_MESSAGE_COUNT, v -> assertThat(v).isGreaterThan(0)));
                      attrs.add(rpcSystemAssertion("grpc"));
                      attrs.addAll(rpcMethodAssertions("example.Greeter", "SayHello"));
                      attrs.add(grpcStatusCodeAssertion(Status.Code.OK.value()));
                      attrs.add(equalTo(SERVER_ADDRESS, "localhost"));
                      attrs.add(equalTo(SERVER_PORT, server.getPort()));
                      attrs.add(equalTo(NETWORK_TYPE, "ipv4"));
                      attrs.add(equalTo(NETWORK_PEER_ADDRESS, "127.0.0.1"));
                      attrs.add(satisfies(NETWORK_PEER_PORT, val -> val.isNotNull()));
                      span.hasName("example.Greeter/SayHello")
                          .hasKind(SpanKind.SERVER)
                          .hasParent(trace.getSpan(1))
                          .hasAttributesSatisfyingExactly(attrs.toArray(new AttributeAssertion[0]))
                          .hasEventsSatisfyingExactly(
                              event ->
                                  event
                                      .hasName("message")
                                      .hasAttributesSatisfyingExactly(
                                          equalTo(
                                              MessageIncubatingAttributes.MESSAGE_TYPE, "RECEIVED"),
                                          equalTo(MessageIncubatingAttributes.MESSAGE_ID, 1L)),
                              event ->
                                  event
                                      .hasName("message")
                                      .hasAttributesSatisfyingExactly(
                                          equalTo(MessageIncubatingAttributes.MESSAGE_TYPE, "SENT"),
                                          equalTo(MessageIncubatingAttributes.MESSAGE_ID, 1L)));
                    }));

    assertMetrics(server, Status.Code.OK);
  }

  @Test
  void listenableFuture() throws Exception {
    BindableService greeter =
        new GreeterGrpc.GreeterImplBase() {
          @Override
          public void sayHello(
              Helloworld.Request req, StreamObserver<Helloworld.Response> responseObserver) {
            Helloworld.Response reply =
                Helloworld.Response.newBuilder().setMessage("Hello " + req.getName()).build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
          }
        };

    Server server = configureServer(ServerBuilder.forPort(0).addService(greeter)).build().start();
    ManagedChannel channel = createChannel(server);
    closer.add(() -> channel.shutdownNow().awaitTermination(10, TimeUnit.SECONDS));
    closer.add(() -> server.shutdownNow().awaitTermination());

    GreeterGrpc.GreeterFutureStub client = GreeterGrpc.newFutureStub(channel);

    AtomicReference<Helloworld.Response> response = new AtomicReference<>();
    AtomicReference<Throwable> error = new AtomicReference<>();
    Helloworld.Request request = Helloworld.Request.newBuilder().setName("test").build();
    testing()
        .runWithSpan(
            "parent",
            () -> {
              ListenableFuture<Helloworld.Response> future =
                  Futures.transform(
                      client.sayHello(request),
                      resp -> {
                        testing().runWithSpan("child", () -> {});
                        return resp;
                      },
                      MoreExecutors.directExecutor());
              try {
                response.set(Futures.getUnchecked(future));
              } catch (Throwable t) {
                error.set(t);
              }
            });

    assertThat(error).hasValue(null);
    Helloworld.Response res = response.get();
    assertThat(res.getMessage()).isEqualTo("Hello test");

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                    span -> {
                      List<AttributeAssertion> attrs = new ArrayList<>();
                      attrs.add(
                          experimentalSatisfies(
                              GRPC_RECEIVED_MESSAGE_COUNT, v -> assertThat(v).isGreaterThan(0)));
                      attrs.add(
                          experimentalSatisfies(
                              GRPC_SENT_MESSAGE_COUNT, v -> assertThat(v).isGreaterThan(0)));
                      attrs.add(rpcSystemAssertion("grpc"));
                      attrs.addAll(rpcMethodAssertions("example.Greeter", "SayHello"));
                      attrs.add(grpcStatusCodeAssertion(Status.Code.OK.value()));
                      attrs.add(equalTo(SERVER_ADDRESS, "localhost"));
                      attrs.add(equalTo(SERVER_PORT, (long) server.getPort()));
                      span.hasName("example.Greeter/SayHello")
                          .hasKind(SpanKind.CLIENT)
                          .hasParent(trace.getSpan(0))
                          .hasAttributesSatisfyingExactly(
                              addExtraClientAttributes(attrs.toArray(new AttributeAssertion[0])))
                          .hasEventsSatisfyingExactly(
                              event ->
                                  event
                                      .hasName("message")
                                      .hasAttributesSatisfyingExactly(
                                          equalTo(MessageIncubatingAttributes.MESSAGE_TYPE, "SENT"),
                                          equalTo(MessageIncubatingAttributes.MESSAGE_ID, 1L)),
                              event ->
                                  event
                                      .hasName("message")
                                      .hasAttributesSatisfyingExactly(
                                          equalTo(
                                              MessageIncubatingAttributes.MESSAGE_TYPE, "RECEIVED"),
                                          equalTo(MessageIncubatingAttributes.MESSAGE_ID, 1L)));
                    },
                    span -> {
                      List<AttributeAssertion> attrs = new ArrayList<>();
                      attrs.add(
                          experimentalSatisfies(
                              GRPC_RECEIVED_MESSAGE_COUNT, v -> assertThat(v).isGreaterThan(0)));
                      attrs.add(
                          experimentalSatisfies(
                              GRPC_SENT_MESSAGE_COUNT, v -> assertThat(v).isGreaterThan(0)));
                      attrs.add(rpcSystemAssertion("grpc"));
                      attrs.addAll(rpcMethodAssertions("example.Greeter", "SayHello"));
                      attrs.add(grpcStatusCodeAssertion(Status.Code.OK.value()));
                      attrs.add(equalTo(SERVER_ADDRESS, "localhost"));
                      attrs.add(equalTo(SERVER_PORT, server.getPort()));
                      attrs.add(equalTo(NETWORK_TYPE, "ipv4"));
                      attrs.add(equalTo(NETWORK_PEER_ADDRESS, "127.0.0.1"));
                      attrs.add(satisfies(NETWORK_PEER_PORT, val -> val.isNotNull()));
                      span.hasName("example.Greeter/SayHello")
                          .hasKind(SpanKind.SERVER)
                          .hasParent(trace.getSpan(1))
                          .hasAttributesSatisfyingExactly(attrs.toArray(new AttributeAssertion[0]))
                          .hasEventsSatisfyingExactly(
                              event ->
                                  event
                                      .hasName("message")
                                      .hasAttributesSatisfyingExactly(
                                          equalTo(
                                              MessageIncubatingAttributes.MESSAGE_TYPE, "RECEIVED"),
                                          equalTo(MessageIncubatingAttributes.MESSAGE_ID, 1L)),
                              event ->
                                  event
                                      .hasName("message")
                                      .hasAttributesSatisfyingExactly(
                                          equalTo(MessageIncubatingAttributes.MESSAGE_TYPE, "SENT"),
                                          equalTo(MessageIncubatingAttributes.MESSAGE_ID, 1L)));
                    },
                    span ->
                        span.hasName("child")
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(0))));

    assertMetrics(server, Status.Code.OK);
  }

  @Test
  void streamingStub() throws Exception {
    BindableService greeter =
        new GreeterGrpc.GreeterImplBase() {
          @Override
          public void sayHello(
              Helloworld.Request req, StreamObserver<Helloworld.Response> responseObserver) {
            Helloworld.Response reply =
                Helloworld.Response.newBuilder().setMessage("Hello " + req.getName()).build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
          }
        };

    Server server = configureServer(ServerBuilder.forPort(0).addService(greeter)).build().start();
    ManagedChannel channel = createChannel(server);
    closer.add(() -> channel.shutdownNow().awaitTermination(10, TimeUnit.SECONDS));
    closer.add(() -> server.shutdownNow().awaitTermination());

    GreeterGrpc.GreeterStub client = GreeterGrpc.newStub(channel);

    AtomicReference<Helloworld.Response> response = new AtomicReference<>();
    AtomicReference<Throwable> error = new AtomicReference<>();
    CountDownLatch latch = new CountDownLatch(1);
    Helloworld.Request request = Helloworld.Request.newBuilder().setName("test").build();
    testing()
        .runWithSpan(
            "parent",
            () ->
                client.sayHello(
                    request,
                    new StreamObserver<Helloworld.Response>() {
                      @Override
                      public void onNext(Helloworld.Response r) {
                        response.set(r);
                      }

                      @Override
                      public void onError(Throwable throwable) {
                        error.set(throwable);
                      }

                      @Override
                      public void onCompleted() {
                        testing().runWithSpan("child", () -> {});
                        latch.countDown();
                      }
                    }));

    latch.await(10, TimeUnit.SECONDS);

    assertThat(error).hasValue(null);
    Helloworld.Response res = response.get();
    assertThat(res.getMessage()).isEqualTo("Hello test");

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                    span -> {
                      List<AttributeAssertion> attrs = new ArrayList<>();
                      attrs.add(
                          experimentalSatisfies(
                              GRPC_RECEIVED_MESSAGE_COUNT, v -> assertThat(v).isGreaterThan(0)));
                      attrs.add(
                          experimentalSatisfies(
                              GRPC_SENT_MESSAGE_COUNT, v -> assertThat(v).isGreaterThan(0)));
                      attrs.add(rpcSystemAssertion("grpc"));
                      attrs.addAll(rpcMethodAssertions("example.Greeter", "SayHello"));
                      attrs.add(grpcStatusCodeAssertion(Status.Code.OK.value()));
                      attrs.add(equalTo(SERVER_ADDRESS, "localhost"));
                      attrs.add(equalTo(SERVER_PORT, (long) server.getPort()));
                      span.hasName("example.Greeter/SayHello")
                          .hasKind(SpanKind.CLIENT)
                          .hasParent(trace.getSpan(0))
                          .hasAttributesSatisfyingExactly(
                              addExtraClientAttributes(attrs.toArray(new AttributeAssertion[0])))
                          .hasEventsSatisfyingExactly(
                              event ->
                                  event
                                      .hasName("message")
                                      .hasAttributesSatisfyingExactly(
                                          equalTo(MessageIncubatingAttributes.MESSAGE_TYPE, "SENT"),
                                          equalTo(MessageIncubatingAttributes.MESSAGE_ID, 1L)),
                              event ->
                                  event
                                      .hasName("message")
                                      .hasAttributesSatisfyingExactly(
                                          equalTo(
                                              MessageIncubatingAttributes.MESSAGE_TYPE, "RECEIVED"),
                                          equalTo(MessageIncubatingAttributes.MESSAGE_ID, 1L)));
                    },
                    span -> {
                      List<AttributeAssertion> attrs = new ArrayList<>();
                      attrs.add(
                          experimentalSatisfies(
                              GRPC_RECEIVED_MESSAGE_COUNT, v -> assertThat(v).isGreaterThan(0)));
                      attrs.add(
                          experimentalSatisfies(
                              GRPC_SENT_MESSAGE_COUNT, v -> assertThat(v).isGreaterThan(0)));
                      attrs.add(rpcSystemAssertion("grpc"));
                      attrs.addAll(rpcMethodAssertions("example.Greeter", "SayHello"));
                      attrs.add(grpcStatusCodeAssertion(Status.Code.OK.value()));
                      attrs.add(equalTo(SERVER_ADDRESS, "localhost"));
                      attrs.add(equalTo(SERVER_PORT, server.getPort()));
                      attrs.add(equalTo(NETWORK_TYPE, "ipv4"));
                      attrs.add(equalTo(NETWORK_PEER_ADDRESS, "127.0.0.1"));
                      attrs.add(satisfies(NETWORK_PEER_PORT, val -> val.isNotNull()));
                      span.hasName("example.Greeter/SayHello")
                          .hasKind(SpanKind.SERVER)
                          .hasParent(trace.getSpan(1))
                          .hasAttributesSatisfyingExactly(attrs.toArray(new AttributeAssertion[0]))
                          .hasEventsSatisfyingExactly(
                              event ->
                                  event
                                      .hasName("message")
                                      .hasAttributesSatisfyingExactly(
                                          equalTo(
                                              MessageIncubatingAttributes.MESSAGE_TYPE, "RECEIVED"),
                                          equalTo(MessageIncubatingAttributes.MESSAGE_ID, 1L)),
                              event ->
                                  event
                                      .hasName("message")
                                      .hasAttributesSatisfyingExactly(
                                          equalTo(MessageIncubatingAttributes.MESSAGE_TYPE, "SENT"),
                                          equalTo(MessageIncubatingAttributes.MESSAGE_ID, 1L)));
                    },
                    span ->
                        span.hasName("child")
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(0))));

    assertMetrics(server, Status.Code.OK);
  }

  @ParameterizedTest
  @MethodSource("provideErrorArguments")
  void errorReturned(Status status) throws Exception {
    BindableService greeter =
        new GreeterGrpc.GreeterImplBase() {
          @Override
          public void sayHello(
              Helloworld.Request req, StreamObserver<Helloworld.Response> responseObserver) {
            responseObserver.onError(status.asException());
          }
        };
    Server server = configureServer(ServerBuilder.forPort(0).addService(greeter)).build().start();
    ManagedChannel channel = createChannel(server);
    closer.add(() -> channel.shutdownNow().awaitTermination(10, TimeUnit.SECONDS));
    closer.add(() -> server.shutdownNow().awaitTermination());

    GreeterGrpc.GreeterBlockingStub client = GreeterGrpc.newBlockingStub(channel);

    Helloworld.Request request = Helloworld.Request.newBuilder().setName("error").build();
    assertThatThrownBy(() -> client.sayHello(request))
        .isInstanceOfSatisfying(
            StatusRuntimeException.class,
            t -> {
              assertThat(t.getStatus().getCode()).isEqualTo(status.getCode());
              assertThat(t.getStatus().getDescription()).isEqualTo(status.getDescription());
            });

    boolean isServerError = status.getCode() != Status.Code.NOT_FOUND;
    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> {
                      List<AttributeAssertion> attrs = new ArrayList<>();
                      attrs.add(
                          experimentalSatisfies(
                              GRPC_RECEIVED_MESSAGE_COUNT, v -> assertThat(v).isEqualTo(0)));
                      attrs.add(
                          experimentalSatisfies(
                              GRPC_SENT_MESSAGE_COUNT, v -> assertThat(v).isGreaterThan(0)));
                      attrs.add(rpcSystemAssertion("grpc"));
                      attrs.addAll(rpcMethodAssertions("example.Greeter", "SayHello"));
                      attrs.add(grpcStatusCodeAssertion(status.getCode().value()));
                      attrs.add(equalTo(SERVER_ADDRESS, "localhost"));
                      attrs.add(equalTo(SERVER_PORT, (long) server.getPort()));
                      span.hasName("example.Greeter/SayHello")
                          .hasKind(SpanKind.CLIENT)
                          .hasNoParent()
                          .hasStatus(StatusData.error())
                          .hasAttributesSatisfyingExactly(
                              addExtraClientAttributes(attrs.toArray(new AttributeAssertion[0])))
                          .hasEventsSatisfyingExactly(
                              event ->
                                  event
                                      .hasName("message")
                                      .hasAttributesSatisfyingExactly(
                                          equalTo(MessageIncubatingAttributes.MESSAGE_TYPE, "SENT"),
                                          equalTo(MessageIncubatingAttributes.MESSAGE_ID, 1L)));
                    },
                    span -> {
                      List<AttributeAssertion> attrs = new ArrayList<>();
                      attrs.add(
                          experimentalSatisfies(
                              GRPC_RECEIVED_MESSAGE_COUNT, v -> assertThat(v).isGreaterThan(0)));
                      attrs.add(
                          experimentalSatisfies(
                              GRPC_SENT_MESSAGE_COUNT, v -> assertThat(v).isEqualTo(0)));
                      attrs.add(rpcSystemAssertion("grpc"));
                      attrs.addAll(rpcMethodAssertions("example.Greeter", "SayHello"));
                      attrs.add(grpcStatusCodeAssertion(status.getCode().value()));
                      attrs.add(equalTo(SERVER_ADDRESS, "localhost"));
                      attrs.add(equalTo(SERVER_PORT, server.getPort()));
                      attrs.add(equalTo(NETWORK_TYPE, "ipv4"));
                      attrs.add(equalTo(NETWORK_PEER_ADDRESS, "127.0.0.1"));
                      attrs.add(satisfies(NETWORK_PEER_PORT, val -> val.isNotNull()));
                      // error.type is added automatically in stable semconv when there's an
                      // exception
                      if (status.getCause() != null) {
                        attrs.addAll(errorTypeAssertion(status.getCause().getClass().getName()));
                      }
                      span.hasName("example.Greeter/SayHello")
                          .hasKind(SpanKind.SERVER)
                          .hasParent(trace.getSpan(0))
                          .hasStatus(isServerError ? StatusData.error() : StatusData.unset())
                          .hasAttributesSatisfyingExactly(attrs.toArray(new AttributeAssertion[0]))
                          .hasEventsSatisfying(
                              events -> {
                                assertThat(events).isNotEmpty();
                                assertThat(events.get(0))
                                    .hasName("message")
                                    .hasAttributesSatisfyingExactly(
                                        equalTo(
                                            MessageIncubatingAttributes.MESSAGE_TYPE, "RECEIVED"),
                                        equalTo(MessageIncubatingAttributes.MESSAGE_ID, 1L));
                                if (status.getCause() == null) {
                                  assertThat(events).hasSize(1);
                                } else {
                                  assertThat(events).hasSize(2);
                                  span.hasException(status.getCause());
                                }
                              });
                    }));

    assertMetrics(server, status.getCode());
  }

  @ParameterizedTest
  @MethodSource("provideErrorArguments")
  void errorThrown(Status status) throws Exception {
    BindableService greeter =
        new GreeterGrpc.GreeterImplBase() {
          @Override
          public void sayHello(
              Helloworld.Request req, StreamObserver<Helloworld.Response> responseObserver) {
            throw status.asRuntimeException();
          }
        };
    Server server = configureServer(ServerBuilder.forPort(0).addService(greeter)).build().start();
    ManagedChannel channel = createChannel(server);
    closer.add(() -> channel.shutdownNow().awaitTermination(10, TimeUnit.SECONDS));
    closer.add(() -> server.shutdownNow().awaitTermination());

    GreeterGrpc.GreeterBlockingStub client = GreeterGrpc.newBlockingStub(channel);
    Helloworld.Request request = Helloworld.Request.newBuilder().setName("error").build();
    assertThatThrownBy(() -> client.sayHello(request))
        .isInstanceOfSatisfying(
            StatusRuntimeException.class,
            t -> {
              // gRPC doesn't appear to propagate server exceptions that are thrown, not onError.
              assertThat(t.getStatus().getCode()).isEqualTo(Status.UNKNOWN.getCode());
              assertThat(t.getStatus().getDescription())
                  .satisfiesAnyOf(
                      a -> assertThat(a).isNull(),
                      a -> assertThat(a).isEqualTo("Application error processing RPC"));
            });

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> {
                      // NB: Exceptions thrown on the server don't appear to be propagated to the
                      // client, at
                      // least for the version we test against, so the client gets an UNKNOWN
                      // status and the server
                      // doesn't record one at all.
                      List<AttributeAssertion> attrs = new ArrayList<>();
                      attrs.add(
                          experimentalSatisfies(
                              GRPC_RECEIVED_MESSAGE_COUNT, v -> assertThat(v).isEqualTo(0)));
                      attrs.add(
                          experimentalSatisfies(
                              GRPC_SENT_MESSAGE_COUNT, v -> assertThat(v).isGreaterThan(0)));
                      attrs.add(rpcSystemAssertion("grpc"));
                      attrs.addAll(rpcMethodAssertions("example.Greeter", "SayHello"));
                      attrs.add(grpcStatusCodeAssertion(Status.UNKNOWN.getCode().value()));
                      attrs.add(equalTo(SERVER_ADDRESS, "localhost"));
                      attrs.add(equalTo(SERVER_PORT, (long) server.getPort()));
                      span.hasName("example.Greeter/SayHello")
                          .hasKind(SpanKind.CLIENT)
                          .hasNoParent()
                          .hasStatus(StatusData.error())
                          .hasAttributesSatisfyingExactly(
                              addExtraClientAttributes(attrs.toArray(new AttributeAssertion[0])))
                          .hasEventsSatisfyingExactly(
                              event ->
                                  event
                                      .hasName("message")
                                      .hasAttributesSatisfyingExactly(
                                          equalTo(MessageIncubatingAttributes.MESSAGE_TYPE, "SENT"),
                                          equalTo(MessageIncubatingAttributes.MESSAGE_ID, 1L)));
                    },
                    span -> {
                      List<AttributeAssertion> attrs = new ArrayList<>();
                      attrs.add(rpcSystemAssertion("grpc"));
                      attrs.addAll(rpcMethodAssertions("example.Greeter", "SayHello"));
                      attrs.add(grpcStatusCodeAssertion(Status.Code.UNKNOWN.value()));
                      attrs.add(equalTo(SERVER_ADDRESS, "localhost"));
                      attrs.add(equalTo(SERVER_PORT, server.getPort()));
                      attrs.add(equalTo(NETWORK_TYPE, "ipv4"));
                      attrs.add(equalTo(NETWORK_PEER_ADDRESS, "127.0.0.1"));
                      attrs.add(satisfies(NETWORK_PEER_PORT, val -> val.isNotNull()));
                      // error.type is added automatically in stable semconv
                      attrs.addAll(errorTypeAssertion("io.grpc.StatusRuntimeException"));
                      span.hasName("example.Greeter/SayHello")
                          .hasKind(SpanKind.SERVER)
                          .hasParent(trace.getSpan(0))
                          .hasStatus(StatusData.error())
                          .hasAttributesSatisfyingExactly(attrs.toArray(new AttributeAssertion[0]))
                          .hasEventsSatisfying(
                              events -> {
                                assertThat(events).hasSize(2);
                                assertThat(events.get(0))
                                    .hasName("message")
                                    .hasAttributesSatisfyingExactly(
                                        equalTo(
                                            MessageIncubatingAttributes.MESSAGE_TYPE, "RECEIVED"),
                                        equalTo(MessageIncubatingAttributes.MESSAGE_ID, 1L));
                                span.hasException(status.asRuntimeException());
                              });
                    }));

    assertMetrics(server, Status.Code.UNKNOWN);
  }

  private static Stream<Arguments> provideErrorArguments() {
    return Stream.of(
        arguments(Status.UNKNOWN.withCause(new RuntimeException("some error"))),
        arguments(Status.DEADLINE_EXCEEDED.withCause(new RuntimeException("some error"))),
        arguments(Status.UNIMPLEMENTED.withCause(new RuntimeException("some error"))),
        arguments(Status.INTERNAL.withCause(new RuntimeException("some error"))),
        arguments(Status.UNAVAILABLE.withCause(new RuntimeException("some error"))),
        arguments(Status.DATA_LOSS.withCause(new RuntimeException("some error"))),
        arguments(Status.NOT_FOUND.withCause(new RuntimeException("some error"))),
        arguments(Status.UNKNOWN.withDescription("some description")),
        arguments(Status.DEADLINE_EXCEEDED.withDescription("some description")),
        arguments(Status.UNIMPLEMENTED.withDescription("some description")),
        arguments(Status.INTERNAL.withDescription("some description")),
        arguments(Status.UNAVAILABLE.withDescription("some description")),
        arguments(Status.DATA_LOSS.withDescription("some description")),
        arguments(Status.NOT_FOUND.withDescription("some description")));
  }

  @Test
  void userContextPreserved() throws Exception {
    Context.Key<String> key = Context.key("cat");
    BindableService greeter =
        new GreeterGrpc.GreeterImplBase() {
          @Override
          public void sayHello(
              Helloworld.Request req, StreamObserver<Helloworld.Response> responseObserver) {
            if (!key.get().equals("meow")) {
              responseObserver.onError(new AssertionError("context not preserved"));
              return;
            }
            if (!Span.fromContext(io.opentelemetry.context.Context.current())
                .getSpanContext()
                .isValid()) {
              responseObserver.onError(new AssertionError("span not attached"));
              return;
            }
            Helloworld.Response reply =
                Helloworld.Response.newBuilder().setMessage("Hello " + req.getName()).build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
          }
        };
    Server server =
        configureServer(
                ServerBuilder.forPort(0)
                    .addService(greeter)
                    .intercept(
                        new ServerInterceptor() {
                          @Override
                          public <REQ, RESP> ServerCall.Listener<REQ> interceptCall(
                              ServerCall<REQ, RESP> call,
                              Metadata headers,
                              ServerCallHandler<REQ, RESP> next) {
                            if (!Span.fromContext(io.opentelemetry.context.Context.current())
                                .getSpanContext()
                                .isValid()) {
                              throw new AssertionError("span not attached in server interceptor");
                            }
                            Context ctx = Context.current().withValue(key, "meow");
                            return Contexts.interceptCall(ctx, call, headers, next);
                          }
                        }))
            .build()
            .start();
    ManagedChannel channel =
        createChannel(
            configureClient(ManagedChannelBuilder.forAddress("localhost", server.getPort()))
                .intercept(
                    new ClientInterceptor() {
                      @Override
                      public <REQ, RESP> ClientCall<REQ, RESP> interceptCall(
                          MethodDescriptor<REQ, RESP> method,
                          CallOptions callOptions,
                          Channel next) {
                        if (!Span.fromContext(io.opentelemetry.context.Context.current())
                            .getSpanContext()
                            .isValid()) {
                          throw new AssertionError("span not attached in client interceptor");
                        }
                        Context ctx = Context.current().withValue(key, "meow");
                        Context oldCtx = ctx.attach();
                        try {
                          return next.newCall(method, callOptions);
                        } finally {
                          ctx.detach(oldCtx);
                        }
                      }
                    }));
    closer.add(() -> channel.shutdownNow().awaitTermination(10, TimeUnit.SECONDS));
    closer.add(() -> server.shutdownNow().awaitTermination());

    GreeterGrpc.GreeterStub client = GreeterGrpc.newStub(channel);

    AtomicReference<Helloworld.Response> response = new AtomicReference<>();
    AtomicReference<Throwable> error = new AtomicReference<>();
    CountDownLatch latch = new CountDownLatch(1);
    Helloworld.Request request = Helloworld.Request.newBuilder().setName("test").build();
    testing()
        .runWithSpan(
            "parent",
            () ->
                client.sayHello(
                    request,
                    new StreamObserver<Helloworld.Response>() {
                      @Override
                      public void onNext(Helloworld.Response r) {
                        if (!key.get().equals("meow")) {
                          error.set(new AssertionError("context not preserved"));
                          return;
                        }
                        if (!Span.fromContext(io.opentelemetry.context.Context.current())
                            .getSpanContext()
                            .isValid()) {
                          error.set(new AssertionError("span not attached"));
                          return;
                        }
                        response.set(r);
                      }

                      @Override
                      public void onError(Throwable throwable) {
                        error.set(throwable);
                      }

                      @Override
                      public void onCompleted() {
                        latch.countDown();
                      }
                    }));

    latch.await(10, TimeUnit.SECONDS);

    assertThat(error).hasValue(null);
    Helloworld.Response res = response.get();
    assertThat(res.getMessage()).isEqualTo("Hello test");

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                    span -> {
                      List<AttributeAssertion> attrs = new ArrayList<>();
                      attrs.add(
                          experimentalSatisfies(
                              GRPC_RECEIVED_MESSAGE_COUNT, v -> assertThat(v).isGreaterThan(0)));
                      attrs.add(
                          experimentalSatisfies(
                              GRPC_SENT_MESSAGE_COUNT, v -> assertThat(v).isGreaterThan(0)));
                      attrs.add(rpcSystemAssertion("grpc"));
                      attrs.addAll(rpcMethodAssertions("example.Greeter", "SayHello"));
                      attrs.add(grpcStatusCodeAssertion(Status.Code.OK.value()));
                      attrs.add(equalTo(SERVER_ADDRESS, "localhost"));
                      attrs.add(equalTo(SERVER_PORT, (long) server.getPort()));
                      span.hasName("example.Greeter/SayHello")
                          .hasKind(SpanKind.CLIENT)
                          .hasParent(trace.getSpan(0))
                          .hasAttributesSatisfyingExactly(
                              addExtraClientAttributes(attrs.toArray(new AttributeAssertion[0])))
                          .hasEventsSatisfyingExactly(
                              event ->
                                  event
                                      .hasName("message")
                                      .hasAttributesSatisfyingExactly(
                                          equalTo(MessageIncubatingAttributes.MESSAGE_TYPE, "SENT"),
                                          equalTo(MessageIncubatingAttributes.MESSAGE_ID, 1L)),
                              event ->
                                  event
                                      .hasName("message")
                                      .hasAttributesSatisfyingExactly(
                                          equalTo(
                                              MessageIncubatingAttributes.MESSAGE_TYPE, "RECEIVED"),
                                          equalTo(MessageIncubatingAttributes.MESSAGE_ID, 1L)));
                    },
                    span -> {
                      List<AttributeAssertion> attrs = new ArrayList<>();
                      attrs.add(
                          experimentalSatisfies(
                              GRPC_RECEIVED_MESSAGE_COUNT, v -> assertThat(v).isGreaterThan(0)));
                      attrs.add(
                          experimentalSatisfies(
                              GRPC_SENT_MESSAGE_COUNT, v -> assertThat(v).isGreaterThan(0)));
                      attrs.add(rpcSystemAssertion("grpc"));
                      attrs.addAll(rpcMethodAssertions("example.Greeter", "SayHello"));
                      attrs.add(grpcStatusCodeAssertion(Status.Code.OK.value()));
                      attrs.add(equalTo(SERVER_ADDRESS, "localhost"));
                      attrs.add(equalTo(SERVER_PORT, server.getPort()));
                      attrs.add(equalTo(NETWORK_TYPE, "ipv4"));
                      attrs.add(equalTo(NETWORK_PEER_ADDRESS, "127.0.0.1"));
                      attrs.add(satisfies(NETWORK_PEER_PORT, val -> val.isNotNull()));
                      span.hasName("example.Greeter/SayHello")
                          .hasKind(SpanKind.SERVER)
                          .hasParent(trace.getSpan(1))
                          .hasAttributesSatisfyingExactly(attrs.toArray(new AttributeAssertion[0]))
                          .hasEventsSatisfyingExactly(
                              event ->
                                  event
                                      .hasName("message")
                                      .hasAttributesSatisfyingExactly(
                                          equalTo(
                                              MessageIncubatingAttributes.MESSAGE_TYPE, "RECEIVED"),
                                          equalTo(MessageIncubatingAttributes.MESSAGE_ID, 1L)),
                              event ->
                                  event
                                      .hasName("message")
                                      .hasAttributesSatisfyingExactly(
                                          equalTo(MessageIncubatingAttributes.MESSAGE_TYPE, "SENT"),
                                          equalTo(MessageIncubatingAttributes.MESSAGE_ID, 1L)));
                    }));
  }

  @Test
  void clientErrorThrown() throws Exception {
    BindableService greeter =
        new GreeterGrpc.GreeterImplBase() {
          @Override
          public void sayMultipleHello(
              Helloworld.Request request, StreamObserver<Helloworld.Response> responseObserver) {
            // Send a response but don't complete so client can fail itself
            responseObserver.onNext(Helloworld.Response.getDefaultInstance());
          }
        };

    Server server = configureServer(ServerBuilder.forPort(0).addService(greeter)).build().start();
    ManagedChannel channel = createChannel(server);
    closer.add(() -> channel.shutdownNow().awaitTermination(10, TimeUnit.SECONDS));
    closer.add(() -> server.shutdownNow().awaitTermination());

    GreeterGrpc.GreeterStub client = GreeterGrpc.newStub(channel);

    IllegalStateException thrown = new IllegalStateException("illegal");
    AtomicReference<Helloworld.Response> response = new AtomicReference<>();
    AtomicReference<Throwable> error = new AtomicReference<>();
    CountDownLatch latch = new CountDownLatch(1);
    Helloworld.Request request = Helloworld.Request.newBuilder().setName("test").build();
    testing()
        .runWithSpan(
            "parent",
            () ->
                client.sayMultipleHello(
                    request,
                    new StreamObserver<Helloworld.Response>() {
                      @Override
                      public void onNext(Helloworld.Response r) {
                        response.set(r);
                        throw thrown;
                      }

                      @Override
                      public void onError(Throwable throwable) {
                        error.set(throwable);
                        latch.countDown();
                      }

                      @Override
                      public void onCompleted() {
                        testing().runWithSpan("child", () -> {});
                        latch.countDown();
                      }
                    }));

    latch.await(10, TimeUnit.SECONDS);

    assertThat(error.get()).isNotNull();

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                    span -> {
                      List<AttributeAssertion> attrs = new ArrayList<>();
                      attrs.add(
                          experimentalSatisfies(
                              GRPC_RECEIVED_MESSAGE_COUNT, v -> assertThat(v).isGreaterThan(0)));
                      attrs.add(
                          experimentalSatisfies(
                              GRPC_SENT_MESSAGE_COUNT, v -> assertThat(v).isGreaterThan(0)));
                      attrs.add(rpcSystemAssertion("grpc"));
                      attrs.addAll(rpcMethodAssertions("example.Greeter", "SayMultipleHello"));
                      attrs.add(grpcStatusCodeAssertion(Status.Code.CANCELLED.value()));
                      attrs.add(equalTo(SERVER_ADDRESS, "localhost"));
                      attrs.add(equalTo(SERVER_PORT, (long) server.getPort()));
                      // error.type is added automatically in stable semconv when client throws
                      attrs.addAll(errorTypeAssertion("java.lang.IllegalStateException"));
                      span.hasName("example.Greeter/SayMultipleHello")
                          .hasKind(SpanKind.CLIENT)
                          .hasParent(trace.getSpan(0))
                          .hasStatus(StatusData.error())
                          .hasAttributesSatisfyingExactly(
                              addExtraClientAttributes(attrs.toArray(new AttributeAssertion[0])))
                          .hasEventsSatisfying(
                              events -> {
                                assertThat(events).hasSize(3);
                                assertThat(events.get(0))
                                    .hasName("message")
                                    .hasAttributesSatisfyingExactly(
                                        equalTo(MessageIncubatingAttributes.MESSAGE_TYPE, "SENT"),
                                        equalTo(MessageIncubatingAttributes.MESSAGE_ID, 1L));
                                assertThat(events.get(1))
                                    .hasName("message")
                                    .hasAttributesSatisfyingExactly(
                                        equalTo(
                                            MessageIncubatingAttributes.MESSAGE_TYPE, "RECEIVED"),
                                        equalTo(MessageIncubatingAttributes.MESSAGE_ID, 1L));
                                span.hasException(thrown);
                              });
                    },
                    span -> {
                      List<AttributeAssertion> attrs = new ArrayList<>();
                      attrs.add(
                          experimentalSatisfies(
                              GRPC_RECEIVED_MESSAGE_COUNT, v -> assertThat(v).isGreaterThan(0)));
                      attrs.add(
                          experimentalSatisfies(
                              GRPC_SENT_MESSAGE_COUNT, v -> assertThat(v).isGreaterThan(0)));
                      attrs.add(rpcSystemAssertion("grpc"));
                      attrs.addAll(rpcMethodAssertions("example.Greeter", "SayMultipleHello"));
                      attrs.add(grpcStatusCodeAssertion(Status.Code.CANCELLED.value()));
                      attrs.add(equalTo(SERVER_ADDRESS, "localhost"));
                      attrs.add(equalTo(SERVER_PORT, server.getPort()));
                      attrs.add(equalTo(NETWORK_TYPE, "ipv4"));
                      attrs.add(equalTo(NETWORK_PEER_ADDRESS, "127.0.0.1"));
                      attrs.add(satisfies(NETWORK_PEER_PORT, val -> val.isNotNull()));
                      span.hasName("example.Greeter/SayMultipleHello")
                          .hasKind(SpanKind.SERVER)
                          .hasParent(trace.getSpan(1))
                          .hasAttributesSatisfyingExactly(attrs.toArray(new AttributeAssertion[0]))
                          .hasEventsSatisfyingExactly(
                              event ->
                                  event
                                      .hasName("message")
                                      .hasAttributesSatisfyingExactly(
                                          equalTo(
                                              MessageIncubatingAttributes.MESSAGE_TYPE, "RECEIVED"),
                                          equalTo(MessageIncubatingAttributes.MESSAGE_ID, 1L)),
                              event ->
                                  event
                                      .hasName("message")
                                      .hasAttributesSatisfyingExactly(
                                          equalTo(MessageIncubatingAttributes.MESSAGE_TYPE, "SENT"),
                                          equalTo(MessageIncubatingAttributes.MESSAGE_ID, 1L)));
                    }));
  }

  @Test
  void reflectionService() throws Exception {
    Server server =
        configureServer(ServerBuilder.forPort(0).addService(ProtoReflectionService.newInstance()))
            .build()
            .start();
    ManagedChannel channel = createChannel(server);
    closer.add(() -> channel.shutdownNow().awaitTermination(10, TimeUnit.SECONDS));
    closer.add(() -> server.shutdownNow().awaitTermination());

    ServerReflectionGrpc.ServerReflectionStub client = ServerReflectionGrpc.newStub(channel);

    AtomicReference<ServerReflectionResponse> response = new AtomicReference<>();
    AtomicReference<Throwable> error = new AtomicReference<>();
    CountDownLatch latch = new CountDownLatch(1);

    StreamObserver<ServerReflectionRequest> request =
        client.serverReflectionInfo(
            new StreamObserver<ServerReflectionResponse>() {
              @Override
              public void onNext(ServerReflectionResponse serverReflectionResponse) {
                response.set(serverReflectionResponse);
              }

              @Override
              public void onError(Throwable throwable) {
                error.set(throwable);
                latch.countDown();
              }

              @Override
              public void onCompleted() {
                latch.countDown();
              }
            });

    ServerReflectionRequest serverReflectionRequest =
        ServerReflectionRequest.newBuilder()
            .setListServices("The content will not be checked?")
            .build();
    request.onNext(serverReflectionRequest);
    request.onCompleted();

    latch.await(10, TimeUnit.SECONDS);

    assertThat(error).hasValue(null);
    ServerReflectionResponse serverReflectionResponse = response.get();
    assertThat(serverReflectionResponse.getListServicesResponse().getService(0).getName())
        .isEqualTo("grpc.reflection.v1alpha.ServerReflection");

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> {
                      List<AttributeAssertion> attrs = new ArrayList<>();
                      attrs.add(
                          experimentalSatisfies(
                              GRPC_RECEIVED_MESSAGE_COUNT, v -> assertThat(v).isGreaterThan(0)));
                      attrs.add(
                          experimentalSatisfies(
                              GRPC_SENT_MESSAGE_COUNT, v -> assertThat(v).isGreaterThan(0)));
                      attrs.add(rpcSystemAssertion("grpc"));
                      attrs.addAll(
                          rpcMethodAssertions(
                              "grpc.reflection.v1alpha.ServerReflection", "ServerReflectionInfo"));
                      attrs.add(grpcStatusCodeAssertion(Status.Code.OK.value()));
                      attrs.add(equalTo(SERVER_ADDRESS, "localhost"));
                      attrs.add(equalTo(SERVER_PORT, (long) server.getPort()));
                      span.hasName("grpc.reflection.v1alpha.ServerReflection/ServerReflectionInfo")
                          .hasKind(SpanKind.CLIENT)
                          .hasNoParent()
                          .hasAttributesSatisfyingExactly(
                              addExtraClientAttributes(attrs.toArray(new AttributeAssertion[0])))
                          .hasEventsSatisfyingExactly(
                              event ->
                                  event
                                      .hasName("message")
                                      .hasAttributesSatisfyingExactly(
                                          equalTo(MessageIncubatingAttributes.MESSAGE_TYPE, "SENT"),
                                          equalTo(MessageIncubatingAttributes.MESSAGE_ID, 1L)),
                              event ->
                                  event
                                      .hasName("message")
                                      .hasAttributesSatisfyingExactly(
                                          equalTo(
                                              MessageIncubatingAttributes.MESSAGE_TYPE, "RECEIVED"),
                                          equalTo(MessageIncubatingAttributes.MESSAGE_ID, 1L)));
                    },
                    span -> {
                      List<AttributeAssertion> attrs = new ArrayList<>();
                      attrs.add(
                          experimentalSatisfies(
                              GRPC_RECEIVED_MESSAGE_COUNT, v -> assertThat(v).isGreaterThan(0)));
                      attrs.add(
                          experimentalSatisfies(
                              GRPC_SENT_MESSAGE_COUNT, v -> assertThat(v).isGreaterThan(0)));
                      attrs.add(rpcSystemAssertion("grpc"));
                      attrs.addAll(
                          rpcMethodAssertions(
                              "grpc.reflection.v1alpha.ServerReflection", "ServerReflectionInfo"));
                      attrs.add(grpcStatusCodeAssertion(Status.Code.OK.value()));
                      attrs.add(equalTo(SERVER_ADDRESS, "localhost"));
                      attrs.add(equalTo(SERVER_PORT, server.getPort()));
                      attrs.add(equalTo(NETWORK_TYPE, "ipv4"));
                      attrs.add(equalTo(NETWORK_PEER_ADDRESS, "127.0.0.1"));
                      attrs.add(satisfies(NETWORK_PEER_PORT, val -> val.isNotNull()));
                      span.hasName("grpc.reflection.v1alpha.ServerReflection/ServerReflectionInfo")
                          .hasKind(SpanKind.SERVER)
                          .hasParent(trace.getSpan(0))
                          .hasAttributesSatisfyingExactly(attrs.toArray(new AttributeAssertion[0]))
                          .hasEventsSatisfyingExactly(
                              event ->
                                  event
                                      .hasName("message")
                                      .hasAttributesSatisfyingExactly(
                                          equalTo(
                                              MessageIncubatingAttributes.MESSAGE_TYPE, "RECEIVED"),
                                          equalTo(MessageIncubatingAttributes.MESSAGE_ID, 1L)),
                              event ->
                                  event
                                      .hasName("message")
                                      .hasAttributesSatisfyingExactly(
                                          equalTo(MessageIncubatingAttributes.MESSAGE_TYPE, "SENT"),
                                          equalTo(MessageIncubatingAttributes.MESSAGE_ID, 1L)));
                    }));
  }

  @Test
  void reuseBuilders() throws Exception {
    BindableService greeter =
        new GreeterGrpc.GreeterImplBase() {
          @Override
          public void sayHello(
              Helloworld.Request req, StreamObserver<Helloworld.Response> responseObserver) {
            Helloworld.Response reply =
                Helloworld.Response.newBuilder().setMessage("Hello " + req.getName()).build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
          }
        };
    ServerBuilder<?> serverBuilder = configureServer(ServerBuilder.forPort(0).addService(greeter));
    // Multiple calls to build on same builder
    serverBuilder.build();
    Server server = serverBuilder.build().start();
    ManagedChannelBuilder<?> channelBuilder =
        configureClient(ManagedChannelBuilder.forAddress("localhost", server.getPort()));
    usePlainText(channelBuilder);
    // Multiple calls to build on the same builder
    channelBuilder.build();
    ManagedChannel channel = channelBuilder.build();
    closer.add(() -> channel.shutdownNow().awaitTermination(10, TimeUnit.SECONDS));
    closer.add(() -> server.shutdownNow().awaitTermination());

    GreeterGrpc.GreeterBlockingStub client = GreeterGrpc.newBlockingStub(channel);

    Helloworld.Request request = Helloworld.Request.newBuilder().setName("test").build();
    Helloworld.Response response = testing().runWithSpan("parent", () -> client.sayHello(request));

    assertThat(response.getMessage()).isEqualTo("Hello test");

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                    span -> {
                      List<AttributeAssertion> attrs = new ArrayList<>();
                      attrs.add(
                          experimentalSatisfies(
                              GRPC_RECEIVED_MESSAGE_COUNT, v -> assertThat(v).isGreaterThan(0)));
                      attrs.add(
                          experimentalSatisfies(
                              GRPC_SENT_MESSAGE_COUNT, v -> assertThat(v).isGreaterThan(0)));
                      attrs.add(rpcSystemAssertion("grpc"));
                      attrs.addAll(rpcMethodAssertions("example.Greeter", "SayHello"));
                      attrs.add(grpcStatusCodeAssertion(Status.Code.OK.value()));
                      attrs.add(equalTo(SERVER_ADDRESS, "localhost"));
                      attrs.add(equalTo(SERVER_PORT, (long) server.getPort()));
                      span.hasName("example.Greeter/SayHello")
                          .hasKind(SpanKind.CLIENT)
                          .hasParent(trace.getSpan(0))
                          .hasAttributesSatisfyingExactly(
                              addExtraClientAttributes(attrs.toArray(new AttributeAssertion[0])))
                          .hasEventsSatisfyingExactly(
                              event ->
                                  event
                                      .hasName("message")
                                      .hasAttributesSatisfyingExactly(
                                          equalTo(MessageIncubatingAttributes.MESSAGE_TYPE, "SENT"),
                                          equalTo(MessageIncubatingAttributes.MESSAGE_ID, 1L)),
                              event ->
                                  event
                                      .hasName("message")
                                      .hasAttributesSatisfyingExactly(
                                          equalTo(
                                              MessageIncubatingAttributes.MESSAGE_TYPE, "RECEIVED"),
                                          equalTo(MessageIncubatingAttributes.MESSAGE_ID, 1L)));
                    },
                    span -> {
                      List<AttributeAssertion> attrs = new ArrayList<>();
                      attrs.add(
                          experimentalSatisfies(
                              GRPC_RECEIVED_MESSAGE_COUNT, v -> assertThat(v).isGreaterThan(0)));
                      attrs.add(
                          experimentalSatisfies(
                              GRPC_SENT_MESSAGE_COUNT, v -> assertThat(v).isGreaterThan(0)));
                      attrs.add(rpcSystemAssertion("grpc"));
                      attrs.addAll(rpcMethodAssertions("example.Greeter", "SayHello"));
                      attrs.add(grpcStatusCodeAssertion(Status.Code.OK.value()));
                      attrs.add(equalTo(SERVER_ADDRESS, "localhost"));
                      attrs.add(equalTo(SERVER_PORT, server.getPort()));
                      attrs.add(equalTo(NETWORK_TYPE, "ipv4"));
                      attrs.add(equalTo(NETWORK_PEER_ADDRESS, "127.0.0.1"));
                      attrs.add(satisfies(NETWORK_PEER_PORT, val -> val.isNotNull()));
                      span.hasName("example.Greeter/SayHello")
                          .hasKind(SpanKind.SERVER)
                          .hasParent(trace.getSpan(1))
                          .hasAttributesSatisfyingExactly(attrs.toArray(new AttributeAssertion[0]))
                          .hasEventsSatisfyingExactly(
                              event ->
                                  event
                                      .hasName("message")
                                      .hasAttributesSatisfyingExactly(
                                          equalTo(
                                              MessageIncubatingAttributes.MESSAGE_TYPE, "RECEIVED"),
                                          equalTo(MessageIncubatingAttributes.MESSAGE_ID, 1L)),
                              event ->
                                  event
                                      .hasName("message")
                                      .hasAttributesSatisfyingExactly(
                                          equalTo(MessageIncubatingAttributes.MESSAGE_TYPE, "SENT"),
                                          equalTo(MessageIncubatingAttributes.MESSAGE_ID, 1L)));
                    }));
  }

  // Regression test for
  // https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/4169
  @Test
  void clientCallAfterServerCompleted() throws Exception {
    Server backend =
        configureServer(
                ServerBuilder.forPort(0)
                    .addService(
                        new GreeterGrpc.GreeterImplBase() {
                          @Override
                          public void sayHello(
                              Helloworld.Request request,
                              StreamObserver<Helloworld.Response> responseObserver) {
                            responseObserver.onNext(
                                Helloworld.Response.newBuilder()
                                    .setMessage(request.getName())
                                    .build());
                            responseObserver.onCompleted();
                          }
                        }))
            .build()
            .start();
    ManagedChannel backendChannel = createChannel(backend);
    closer.add(() -> backendChannel.shutdownNow().awaitTermination(10, TimeUnit.SECONDS));
    closer.add(() -> backend.shutdownNow().awaitTermination());
    GreeterGrpc.GreeterBlockingStub backendStub = GreeterGrpc.newBlockingStub(backendChannel);

    // This executor does not propagate context without the javaagent available.
    ExecutorService executor = Executors.newFixedThreadPool(1);
    closer.add(executor::shutdownNow);

    CountDownLatch clientCallDone = new CountDownLatch(1);
    AtomicReference<Throwable> error = new AtomicReference<>();

    Server frontend =
        configureServer(
                ServerBuilder.forPort(0)
                    .addService(
                        new GreeterGrpc.GreeterImplBase() {
                          @Override
                          public void sayHello(
                              Helloworld.Request request,
                              StreamObserver<Helloworld.Response> responseObserver) {
                            responseObserver.onNext(
                                Helloworld.Response.newBuilder()
                                    .setMessage(request.getName())
                                    .build());
                            responseObserver.onCompleted();

                            executor.execute(
                                () -> {
                                  try {
                                    backendStub.sayHello(request);
                                  } catch (Throwable t) {
                                    error.set(t);
                                  }
                                  clientCallDone.countDown();
                                });
                          }
                        }))
            .build()
            .start();
    ManagedChannel frontendChannel = createChannel(frontend);
    closer.add(() -> frontendChannel.shutdownNow().awaitTermination(10, TimeUnit.SECONDS));
    closer.add(() -> frontend.shutdownNow().awaitTermination());

    GreeterGrpc.GreeterBlockingStub frontendStub = GreeterGrpc.newBlockingStub(frontendChannel);
    frontendStub.sayHello(Helloworld.Request.newBuilder().setName("test").build());

    // We don't assert on telemetry - the intention of this test is to verify that adding
    // instrumentation, either as
    // library or javaagent, does not cause exceptions in the business logic. The produced telemetry
    // will be different
    // for the two cases due to lack of context propagation in the library case, but that isn't what
    // we're testing here.

    clientCallDone.await(10, TimeUnit.SECONDS);

    assertThat(error).hasValue(null);
  }

  // Regression test for
  // https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/8923
  @Test
  void cancelListenerCalled() throws Exception {
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch cancelLatch = new CountDownLatch(1);
    AtomicBoolean cancelCalled = new AtomicBoolean();

    Server server =
        configureServer(
                ServerBuilder.forPort(0)
                    .addService(
                        new GreeterGrpc.GreeterImplBase() {
                          @Override
                          public void sayHello(
                              Helloworld.Request request,
                              StreamObserver<Helloworld.Response> responseObserver) {
                            startLatch.countDown();

                            io.grpc.Context context = io.grpc.Context.current();
                            context.addListener(
                                context1 -> cancelCalled.set(true), MoreExecutors.directExecutor());
                            try {
                              cancelLatch.await(10, TimeUnit.SECONDS);
                            } catch (InterruptedException e) {
                              Thread.currentThread().interrupt();
                            }
                            responseObserver.onNext(
                                Helloworld.Response.newBuilder()
                                    .setMessage(request.getName())
                                    .build());
                            responseObserver.onCompleted();
                          }
                        }))
            .build()
            .start();
    ManagedChannel channel = createChannel(server);
    closer.add(() -> channel.shutdownNow().awaitTermination(10, TimeUnit.SECONDS));
    closer.add(() -> server.shutdownNow().awaitTermination());

    GreeterGrpc.GreeterFutureStub client = GreeterGrpc.newFutureStub(channel);
    ListenableFuture<Helloworld.Response> future =
        client.sayHello(Helloworld.Request.newBuilder().setName("test").build());

    startLatch.await(10, TimeUnit.SECONDS);
    future.cancel(false);
    cancelLatch.countDown();

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("example.Greeter/SayHello")
                            .hasKind(SpanKind.CLIENT)
                            .hasNoParent(),
                    span ->
                        span.hasName("example.Greeter/SayHello")
                            .hasKind(SpanKind.SERVER)
                            .hasParent(trace.getSpan(0))));

    assertThat(cancelCalled.get()).isEqualTo(true);
  }

  @Test
  void setCapturedRequestMetadata() throws Exception {
    String metadataAttributePrefix =
        SemconvStability.emitStableRpcSemconv()
            ? "rpc.request.metadata."
            : "rpc.grpc.request.metadata.";
    AttributeKey<List<String>> clientAttributeKey =
        AttributeKey.stringArrayKey(metadataAttributePrefix + CLIENT_REQUEST_METADATA_KEY);
    AttributeKey<List<String>> serverAttributeKey =
        AttributeKey.stringArrayKey(metadataAttributePrefix + SERVER_REQUEST_METADATA_KEY);
    String serverMetadataValue = "server-value";
    String clientMetadataValue = "client-value";

    BindableService greeter =
        new GreeterGrpc.GreeterImplBase() {
          @Override
          public void sayHello(
              Helloworld.Request req, StreamObserver<Helloworld.Response> responseObserver) {
            Helloworld.Response reply =
                Helloworld.Response.newBuilder().setMessage("Hello " + req.getName()).build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
          }
        };

    Server server = configureServer(ServerBuilder.forPort(0).addService(greeter)).build().start();

    ManagedChannel channel = createChannel(server);

    Metadata extraMetadata = new Metadata();
    extraMetadata.put(
        Metadata.Key.of(SERVER_REQUEST_METADATA_KEY, Metadata.ASCII_STRING_MARSHALLER),
        serverMetadataValue);
    extraMetadata.put(
        Metadata.Key.of(CLIENT_REQUEST_METADATA_KEY, Metadata.ASCII_STRING_MARSHALLER),
        clientMetadataValue);

    GreeterGrpc.GreeterBlockingStub client =
        GreeterGrpc.newBlockingStub(channel)
            .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(extraMetadata));

    Helloworld.Response response =
        testing()
            .runWithSpan(
                "parent",
                () -> client.sayHello(Helloworld.Request.newBuilder().setName("test").build()));

    assertThat(response.getMessage()).isEqualTo("Hello test");

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                    span ->
                        span.hasName("example.Greeter/SayHello")
                            .hasKind(SpanKind.CLIENT)
                            .hasParent(trace.getSpan(0))
                            .hasAttribute(
                                clientAttributeKey, Collections.singletonList(clientMetadataValue)),
                    span ->
                        span.hasName("example.Greeter/SayHello")
                            .hasKind(SpanKind.SERVER)
                            .hasParent(trace.getSpan(1))
                            .hasAttribute(
                                serverAttributeKey,
                                Collections.singletonList(serverMetadataValue))));
  }

  private ManagedChannel createChannel(Server server) throws Exception {
    ManagedChannelBuilder<?> channelBuilder =
        configureClient(ManagedChannelBuilder.forAddress("localhost", server.getPort()));
    return createChannel(channelBuilder);
  }

  static ManagedChannel createChannel(ManagedChannelBuilder<?> channelBuilder) throws Exception {
    usePlainText(channelBuilder);
    return channelBuilder.build();
  }

  private static void usePlainText(ManagedChannelBuilder<?> channelBuilder) throws Exception {
    // Depending on the version of gRPC usePlainText may or may not take an argument.
    try {
      channelBuilder
          .getClass()
          .getMethod("usePlaintext", boolean.class)
          .invoke(channelBuilder, true);
    } catch (NoSuchMethodException unused) {
      channelBuilder.getClass().getMethod("usePlaintext").invoke(channelBuilder);
    }
  }

  private static List<AttributeAssertion> buildServerMetricAttributes(
      String service, String method, long statusCode) {
    List<AttributeAssertion> attrs = new ArrayList<>();
    attrs.add(equalTo(SERVER_ADDRESS, "localhost"));
    attrs.add(satisfies(SERVER_PORT, k -> k.isInstanceOf(Long.class)));
    attrs.add(rpcSystemAssertion("grpc"));
    attrs.addAll(rpcMethodAssertions(service, method));
    attrs.add(grpcStatusCodeAssertion(statusCode));
    if (SemconvStability.emitOldRpcSemconv()) {
      attrs.add(equalTo(NETWORK_TYPE, "ipv4"));
    }
    return attrs;
  }

  private static List<AttributeAssertion> buildClientMetricAttributes(
      String service, String method, long statusCode, long serverPort) {
    List<AttributeAssertion> attrs = new ArrayList<>();
    attrs.add(equalTo(SERVER_ADDRESS, "localhost"));
    attrs.add(equalTo(SERVER_PORT, serverPort));
    attrs.add(rpcSystemAssertion("grpc"));
    attrs.addAll(rpcMethodAssertions(service, method));
    attrs.add(grpcStatusCodeAssertion(statusCode));
    return attrs;
  }

  static List<AttributeAssertion> addExtraClientAttributes(AttributeAssertion... assertions) {
    List<AttributeAssertion> result = new ArrayList<>();
    result.addAll(Arrays.asList(assertions));
    if (Boolean.getBoolean("testLatestDeps")) {
      result.add(equalTo(NETWORK_TYPE, "ipv4"));
      result.add(equalTo(NETWORK_PEER_ADDRESS, "127.0.0.1"));
      result.add(satisfies(NETWORK_PEER_PORT, val -> val.isNotNull()));
    }
    return result;
  }

  private void assertMetrics(Server server, Status.Code statusCode) {
    boolean hasSizeMetric = statusCode == Status.Code.OK;
    testing()
        .waitAndAssertMetrics(
            "io.opentelemetry.grpc-1.6",
            getServerDurationMetricName(),
            metrics ->
                metrics.anySatisfy(
                    metric ->
                        assertThat(metric)
                            .hasUnit(getDurationUnit())
                            .hasHistogramSatisfying(
                                histogram ->
                                    histogram.hasPointsSatisfying(
                                        point ->
                                            point.hasAttributesSatisfyingExactly(
                                                buildServerMetricAttributes(
                                                        "example.Greeter",
                                                        "SayHello",
                                                        (long) statusCode.value())
                                                    .toArray(new AttributeAssertion[0]))))));

    // Size metrics are only in old semconv
    if (hasSizeMetric && SemconvStability.emitOldRpcSemconv()) {
      testing()
          .waitAndAssertMetrics(
              "io.opentelemetry.grpc-1.6",
              "rpc.server.request.size",
              metrics ->
                  metrics.anySatisfy(
                      metric ->
                          assertThat(metric)
                              .hasUnit("By")
                              .hasHistogramSatisfying(
                                  histogram ->
                                      histogram.hasPointsSatisfying(
                                          point ->
                                              point.hasAttributesSatisfyingExactly(
                                                  buildServerMetricAttributes(
                                                          "example.Greeter",
                                                          "SayHello",
                                                          (long) statusCode.value())
                                                      .toArray(new AttributeAssertion[0]))))));
      testing()
          .waitAndAssertMetrics(
              "io.opentelemetry.grpc-1.6",
              "rpc.server.response.size",
              metrics ->
                  metrics.anySatisfy(
                      metric ->
                          assertThat(metric)
                              .hasUnit("By")
                              .hasHistogramSatisfying(
                                  histogram ->
                                      histogram.hasPointsSatisfying(
                                          point ->
                                              point.hasAttributesSatisfyingExactly(
                                                  buildServerMetricAttributes(
                                                          "example.Greeter",
                                                          "SayHello",
                                                          (long) statusCode.value())
                                                      .toArray(new AttributeAssertion[0]))))));
    }

    testing()
        .waitAndAssertMetrics(
            "io.opentelemetry.grpc-1.6",
            getClientDurationMetricName(),
            metrics ->
                metrics.anySatisfy(
                    metric ->
                        assertThat(metric)
                            .hasUnit(getDurationUnit())
                            .hasHistogramSatisfying(
                                histogram ->
                                    histogram.hasPointsSatisfying(
                                        point ->
                                            point.hasAttributesSatisfying(
                                                buildClientMetricAttributes(
                                                        "example.Greeter",
                                                        "SayHello",
                                                        (long) statusCode.value(),
                                                        server.getPort())
                                                    .toArray(new AttributeAssertion[0]))))));

    // Size metrics are only in old semconv
    if (hasSizeMetric && SemconvStability.emitOldRpcSemconv()) {
      testing()
          .waitAndAssertMetrics(
              "io.opentelemetry.grpc-1.6",
              "rpc.client.request.size",
              metrics ->
                  metrics.anySatisfy(
                      metric ->
                          assertThat(metric)
                              .hasUnit("By")
                              .hasHistogramSatisfying(
                                  histogram ->
                                      histogram.hasPointsSatisfying(
                                          point ->
                                              point.hasAttributesSatisfying(
                                                  buildClientMetricAttributes(
                                                          "example.Greeter",
                                                          "SayHello",
                                                          (long) statusCode.value(),
                                                          server.getPort())
                                                      .toArray(new AttributeAssertion[0]))))));
    }
    if (hasSizeMetric && SemconvStability.emitOldRpcSemconv()) {
      testing()
          .waitAndAssertMetrics(
              "io.opentelemetry.grpc-1.6",
              "rpc.client.response.size",
              metrics ->
                  metrics.anySatisfy(
                      metric ->
                          assertThat(metric)
                              .hasUnit("By")
                              .hasHistogramSatisfying(
                                  histogram ->
                                      histogram.hasPointsSatisfying(
                                          point ->
                                              point.hasAttributesSatisfying(
                                                  buildClientMetricAttributes(
                                                          "example.Greeter",
                                                          "SayHello",
                                                          (long) statusCode.value(),
                                                          server.getPort())
                                                      .toArray(new AttributeAssertion[0]))))));
    }
  }
}
