/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_6;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;

import example.GreeterGrpc;
import example.Helloworld;
import io.grpc.BindableService;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.util.ThrowingRunnable;
import io.opentelemetry.sdk.testing.assertj.EventDataAssert;
import io.opentelemetry.sdk.testing.assertj.MetricAssertions;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
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

  @CartesianTest
  @SuppressWarnings({"unchecked", "rawtypes"})
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

    List<Consumer<EventDataAssert>> events = new ArrayList<>();
    for (int i = 1; i <= clientMessageCount * serverMessageCount + clientMessageCount; i++) {
      long messageId = i;
      events.add(
          event ->
              event
                  .hasName("message")
                  .hasAttributesSatisfying(
                      attrs ->
                          assertThat(attrs)
                              .hasSize(2)
                              .hasEntrySatisfying(
                                  SemanticAttributes.MESSAGE_TYPE,
                                  val ->
                                      assertThat(val)
                                          .satisfiesAnyOf(
                                              v -> assertThat(v).isEqualTo("RECEIVED"),
                                              v -> assertThat(v).isEqualTo("SENT")))
                              .containsEntry(SemanticAttributes.MESSAGE_ID, messageId)));
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
                                equalTo(SemanticAttributes.RPC_SYSTEM, "grpc"),
                                equalTo(SemanticAttributes.RPC_SERVICE, "example.Greeter"),
                                equalTo(SemanticAttributes.RPC_METHOD, "Conversation"),
                                equalTo(
                                    SemanticAttributes.NET_TRANSPORT,
                                    SemanticAttributes.NetTransportValues.IP_TCP),
                                equalTo(
                                    SemanticAttributes.RPC_GRPC_STATUS_CODE,
                                    (long) Status.Code.OK.value()),
                                equalTo(SemanticAttributes.NET_PEER_NAME, "localhost"),
                                equalTo(SemanticAttributes.NET_PEER_PORT, (long) server.getPort()))
                            .hasEventsSatisfyingExactly(events.toArray(new Consumer[0])),
                    span ->
                        span.hasName("example.Greeter/Conversation")
                            .hasKind(SpanKind.SERVER)
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfyingExactly(
                                equalTo(SemanticAttributes.RPC_SYSTEM, "grpc"),
                                equalTo(SemanticAttributes.RPC_SERVICE, "example.Greeter"),
                                equalTo(SemanticAttributes.RPC_METHOD, "Conversation"),
                                equalTo(SemanticAttributes.NET_PEER_IP, "127.0.0.1"),
                                // net.peer.name resolves to "127.0.0.1" on windows which is same as
                                // net.peer.ip so then not captured
                                satisfies(
                                    SemanticAttributes.NET_PEER_NAME,
                                    val ->
                                        val.satisfiesAnyOf(
                                            v -> assertThat(v).isNull(),
                                            v -> assertThat(v).isEqualTo("localhost"))),
                                satisfies(
                                    SemanticAttributes.NET_PEER_PORT,
                                    val -> assertThat(val).isNotNull()),
                                equalTo(
                                    SemanticAttributes.NET_TRANSPORT,
                                    SemanticAttributes.NetTransportValues.IP_TCP),
                                equalTo(
                                    SemanticAttributes.RPC_GRPC_STATUS_CODE,
                                    (long) Status.Code.OK.value()))
                            .hasEventsSatisfyingExactly(events.toArray(new Consumer[0]))));
    testing()
        .waitAndAssertMetrics(
            "io.opentelemetry.grpc-1.6",
            "rpc.server.duration",
            metrics ->
                metrics.anySatisfy(
                    metric ->
                        MetricAssertions.assertThat(metric)
                            .hasUnit("ms")
                            .hasDoubleHistogram()
                            .points()
                            .anySatisfy(
                                point ->
                                    MetricAssertions.assertThat(point)
                                        .hasAttributes(
                                            Attributes.builder()
                                                .put(SemanticAttributes.NET_TRANSPORT, "ip_tcp")
                                                .put(SemanticAttributes.RPC_METHOD, "Conversation")
                                                .put(
                                                    SemanticAttributes.RPC_SERVICE,
                                                    "example.Greeter")
                                                .put(SemanticAttributes.RPC_SYSTEM, "grpc")
                                                .build()))));
    testing()
        .waitAndAssertMetrics(
            "io.opentelemetry.grpc-1.6",
            "rpc.client.duration",
            metrics ->
                metrics.anySatisfy(
                    metric ->
                        MetricAssertions.assertThat(metric)
                            .hasUnit("ms")
                            .hasDoubleHistogram()
                            .points()
                            .allSatisfy(
                                point ->
                                    MetricAssertions.assertThat(point)
                                        .hasAttributes(
                                            Attributes.builder()
                                                .put(SemanticAttributes.NET_PEER_NAME, "localhost")
                                                .put(
                                                    SemanticAttributes.NET_PEER_PORT,
                                                    server.getPort())
                                                .put(SemanticAttributes.NET_TRANSPORT, "ip_tcp")
                                                .put(SemanticAttributes.RPC_METHOD, "Conversation")
                                                .put(
                                                    SemanticAttributes.RPC_SERVICE,
                                                    "example.Greeter")
                                                .put(SemanticAttributes.RPC_SYSTEM, "grpc")
                                                .build()))));
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
}
