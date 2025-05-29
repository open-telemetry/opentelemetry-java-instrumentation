/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_6;

import static io.opentelemetry.instrumentation.grpc.v1_6.AbstractGrpcTest.addExtraClientAttributes;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_TYPE;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_GRPC_STATUS_CODE;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_METHOD;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SERVICE;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SYSTEM;

import example.GreeterGrpc;
import example.Helloworld;
import io.grpc.BindableService;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.util.ThrowingRunnable;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.semconv.incubating.MessageIncubatingAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.cartesian.CartesianTest;

public abstract class AbstractGrpcStreamingTest {

  protected abstract ServerBuilder<?> configureServer(ServerBuilder<?> server);

  protected abstract ManagedChannelBuilder<?> configureClient(ManagedChannelBuilder<?> client);

  protected abstract InstrumentationExtension testing();

  private final Queue<ThrowingRunnable<?>> closer = new ConcurrentLinkedQueue<>();

  @AfterEach
  void tearDown() throws Throwable {
    while (!closer.isEmpty()) {
      closer.poll().run();
    }
  }

  @SuppressWarnings("deprecation") // using deprecated semconv
  @CartesianTest
  void conversation(
      @CartesianTest.Values(ints = {1, 2, 3}) int clientMessageCount,
      @CartesianTest.Values(ints = {1, 2, 3}) int serverMessageCount)
      throws Exception {
    Queue<String> serverReceived = new ConcurrentLinkedQueue<>();
    Queue<String> clientReceived = new ConcurrentLinkedQueue<>();
    AtomicReference<Throwable> error = new AtomicReference<>();
    CountDownLatch latch = new CountDownLatch(2);

    BindableService greeter =
        new GreeterGrpc.GreeterImplBase() {
          @Override
          public StreamObserver<Helloworld.Response> conversation(
              StreamObserver<Helloworld.Response> observer) {
            return new StreamObserver<Helloworld.Response>() {
              @Override
              public void onNext(Helloworld.Response value) {
                serverReceived.add(value.getMessage());

                for (int i = 1; i <= serverMessageCount; i++) {
                  observer.onNext(value);
                }
              }

              @Override
              public void onError(Throwable t) {
                error.set(t);
                observer.onError(t);
              }

              @Override
              public void onCompleted() {
                observer.onCompleted();
                latch.countDown();
              }
            };
          }
        };

    Server server = configureServer(ServerBuilder.forPort(0).addService(greeter)).build().start();
    ManagedChannel channel = createChannel(server);
    closer.add(() -> channel.shutdownNow().awaitTermination(10, TimeUnit.SECONDS));
    closer.add(() -> server.shutdownNow().awaitTermination());

    GreeterGrpc.GreeterStub client = GreeterGrpc.newStub(channel).withWaitForReady();

    StreamObserver<Helloworld.Response> observer2 =
        client.conversation(
            new StreamObserver<Helloworld.Response>() {
              @Override
              public void onNext(Helloworld.Response value) {
                clientReceived.add(value.getMessage());
              }

              @Override
              public void onError(Throwable t) {
                error.set(t);
              }

              @Override
              public void onCompleted() {
                latch.countDown();
              }
            });

    for (int i = 1; i <= clientMessageCount; i++) {
      Helloworld.Response message =
          Helloworld.Response.newBuilder().setMessage("call " + i).build();
      observer2.onNext(message);
    }
    observer2.onCompleted();

    latch.await(10, TimeUnit.SECONDS);

    assertThat(error).hasValue(null);
    assertThat(serverReceived)
        .containsExactlyElementsOf(
            IntStream.rangeClosed(1, clientMessageCount)
                .mapToObj(i -> "call " + i)
                .collect(Collectors.toList()));
    assertThat(clientReceived)
        .containsExactlyElementsOf(
            IntStream.rangeClosed(1, serverMessageCount)
                .boxed()
                .flatMap(
                    unused ->
                        IntStream.rangeClosed(1, clientMessageCount).mapToObj(i -> "call " + i))
                .sorted()
                .collect(Collectors.toList()));

    List<Consumer<EventData>> clientEvents = new ArrayList<>();
    List<Consumer<EventData>> serverEvents = new ArrayList<>();
    for (long i = 0; i < clientMessageCount; i++) {
      long clientMessageId = i + 1;
      clientEvents.add(
          event ->
              assertThat(event)
                  .hasName("message")
                  .hasAttributesSatisfying(
                      attrs ->
                          assertThat(attrs)
                              .hasSize(2)
                              .containsEntry(MessageIncubatingAttributes.MESSAGE_TYPE, "SENT")
                              .containsEntry(
                                  MessageIncubatingAttributes.MESSAGE_ID, clientMessageId)));
      serverEvents.add(
          event ->
              assertThat(event)
                  .hasName("message")
                  .hasAttributesSatisfying(
                      attrs ->
                          assertThat(attrs)
                              .hasSize(2)
                              .containsEntry(MessageIncubatingAttributes.MESSAGE_TYPE, "RECEIVED")
                              .containsEntry(
                                  MessageIncubatingAttributes.MESSAGE_ID, clientMessageId)));

      for (long j = 0; j < serverMessageCount; j++) {
        long serverMessageId = i * serverMessageCount + j + 1;
        clientEvents.add(
            event ->
                assertThat(event)
                    .hasName("message")
                    .hasAttributesSatisfying(
                        attrs ->
                            assertThat(attrs)
                                .hasSize(2)
                                .containsEntry(MessageIncubatingAttributes.MESSAGE_TYPE, "RECEIVED")
                                .containsEntry(
                                    MessageIncubatingAttributes.MESSAGE_ID, serverMessageId)));
        serverEvents.add(
            event ->
                assertThat(event)
                    .hasName("message")
                    .hasAttributesSatisfying(
                        attrs ->
                            assertThat(attrs)
                                .hasSize(2)
                                .containsEntry(MessageIncubatingAttributes.MESSAGE_TYPE, "SENT")
                                .containsEntry(
                                    MessageIncubatingAttributes.MESSAGE_ID, serverMessageId)));
      }
    }

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("example.Greeter/Conversation")
                            .hasKind(SpanKind.CLIENT)
                            .hasNoParent()
                            .hasAttributesSatisfyingExactly(
                                addExtraClientAttributes(
                                    equalTo(RPC_SYSTEM, "grpc"),
                                    equalTo(RPC_SERVICE, "example.Greeter"),
                                    equalTo(RPC_METHOD, "Conversation"),
                                    equalTo(RPC_GRPC_STATUS_CODE, (long) Status.Code.OK.value()),
                                    equalTo(SERVER_ADDRESS, "localhost"),
                                    equalTo(SERVER_PORT, (long) server.getPort())))
                            .satisfies(
                                spanData ->
                                    assertThat(spanData.getEvents())
                                        .satisfiesExactlyInAnyOrder(toArray(clientEvents))),
                    span ->
                        span.hasName("example.Greeter/Conversation")
                            .hasKind(SpanKind.SERVER)
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfyingExactly(
                                equalTo(RPC_SYSTEM, "grpc"),
                                equalTo(RPC_SERVICE, "example.Greeter"),
                                equalTo(RPC_METHOD, "Conversation"),
                                equalTo(RPC_GRPC_STATUS_CODE, (long) Status.Code.OK.value()),
                                equalTo(SERVER_ADDRESS, "localhost"),
                                equalTo(SERVER_PORT, server.getPort()),
                                equalTo(NETWORK_TYPE, "ipv4"),
                                equalTo(NETWORK_PEER_ADDRESS, "127.0.0.1"),
                                satisfies(NETWORK_PEER_PORT, val -> assertThat(val).isNotNull()))
                            .satisfies(
                                spanData ->
                                    assertThat(spanData.getEvents())
                                        .satisfiesExactlyInAnyOrder(toArray(serverEvents)))));
    testing()
        .waitAndAssertMetrics(
            "io.opentelemetry.grpc-1.6",
            "rpc.server.duration",
            metrics ->
                metrics.anySatisfy(
                    metric ->
                        assertThat(metric)
                            .hasUnit("ms")
                            .hasHistogramSatisfying(
                                histogram ->
                                    histogram.hasPointsSatisfying(
                                        point ->
                                            point.hasAttributesSatisfying(
                                                equalTo(SERVER_ADDRESS, "localhost"),
                                                equalTo(RPC_METHOD, "Conversation"),
                                                equalTo(RPC_SERVICE, "example.Greeter"),
                                                equalTo(RPC_SYSTEM, "grpc"),
                                                equalTo(
                                                    RPC_GRPC_STATUS_CODE,
                                                    (long) Status.Code.OK.value()))))));
    testing()
        .waitAndAssertMetrics(
            "io.opentelemetry.grpc-1.6",
            "rpc.client.duration",
            metrics ->
                metrics.anySatisfy(
                    metric ->
                        assertThat(metric)
                            .hasUnit("ms")
                            .hasHistogramSatisfying(
                                histogram ->
                                    histogram.hasPointsSatisfying(
                                        point ->
                                            point.hasAttributesSatisfying(
                                                equalTo(SERVER_ADDRESS, "localhost"),
                                                equalTo(SERVER_PORT, server.getPort()),
                                                equalTo(RPC_METHOD, "Conversation"),
                                                equalTo(RPC_SERVICE, "example.Greeter"),
                                                equalTo(RPC_SYSTEM, "grpc"),
                                                equalTo(
                                                    RPC_GRPC_STATUS_CODE,
                                                    (long) Status.Code.OK.value()))))));
  }

  @Test
  void grpcServerSpanEndsAfterChildSpan() throws Exception {
    Tracer tracer = testing().getOpenTelemetry().getTracer("test");
    AtomicBoolean serverSpanRecording = new AtomicBoolean();
    CountDownLatch latch = new CountDownLatch(2);

    BindableService greeter =
        new GreeterGrpc.GreeterImplBase() {
          @Override
          public StreamObserver<Helloworld.Response> conversation(
              StreamObserver<Helloworld.Response> observer) {
            return new StreamObserver<Helloworld.Response>() {
              Span span;

              @Override
              public void onNext(Helloworld.Response value) {
                span = tracer.spanBuilder("child").startSpan();
                observer.onNext(value);
              }

              @Override
              public void onError(Throwable t) {
                observer.onError(t);
                span.end();
              }

              @Override
              public void onCompleted() {
                observer.onCompleted();
                serverSpanRecording.set(Span.current().isRecording());
                span.end();
                latch.countDown();
              }
            };
          }
        };

    Server server = configureServer(ServerBuilder.forPort(0).addService(greeter)).build().start();
    ManagedChannel channel = createChannel(server);
    closer.add(() -> channel.shutdownNow().awaitTermination(10, TimeUnit.SECONDS));
    closer.add(() -> server.shutdownNow().awaitTermination());

    GreeterGrpc.GreeterStub client = GreeterGrpc.newStub(channel).withWaitForReady();

    StreamObserver<Helloworld.Response> observer2 =
        client.conversation(
            new StreamObserver<Helloworld.Response>() {
              @Override
              public void onNext(Helloworld.Response value) {}

              @Override
              public void onError(Throwable t) {}

              @Override
              public void onCompleted() {
                latch.countDown();
              }
            });

    Helloworld.Response message = Helloworld.Response.newBuilder().setMessage("message").build();
    observer2.onNext(message);
    observer2.onCompleted();

    latch.await(10, TimeUnit.SECONDS);

    // server span should end after child span
    assertThat(serverSpanRecording).isTrue();

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("example.Greeter/Conversation")
                            .hasKind(SpanKind.CLIENT)
                            .hasNoParent(),
                    span ->
                        span.hasName("example.Greeter/Conversation")
                            .hasKind(SpanKind.SERVER)
                            .hasParent(trace.getSpan(0)),
                    span ->
                        span.hasName("child")
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(1))));
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

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static Consumer<EventData>[] toArray(List<Consumer<EventData>> list) {
    return list.toArray(new Consumer[0]);
  }
}
