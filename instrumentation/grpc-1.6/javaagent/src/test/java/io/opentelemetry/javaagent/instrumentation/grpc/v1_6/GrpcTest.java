/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.grpc.v1_6;

import static io.opentelemetry.instrumentation.testing.util.TestLatestDeps.testLatestDeps;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;

import example.GreeterGrpc;
import example.Helloworld;
import io.grpc.Attributes;
import io.grpc.BindableService;
import io.grpc.EquivalentAddressGroup;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.NameResolver;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.grpc.v1_6.AbstractGrpcTest;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class GrpcTest extends AbstractGrpcTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Override
  protected ServerBuilder<?> configureServer(ServerBuilder<?> server) {
    return server;
  }

  @Override
  protected ManagedChannelBuilder<?> configureClient(ManagedChannelBuilder<?> client) {
    return client;
  }

  @Override
  protected boolean targetCaptureSupported() {
    return testLatestDeps();
  }

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  @Test
  @SuppressWarnings("deprecation")
  void targetCapturedWithCustomNameResolver() throws Exception {
    assumeTrue(targetCaptureSupported());

    BindableService greeter =
        new GreeterGrpc.GreeterImplBase() {
          @Override
          public void sayHello(
              Helloworld.Request req, StreamObserver<Helloworld.Response> responseObserver) {
            responseObserver.onNext(
                Helloworld.Response.newBuilder().setMessage("Hello " + req.getName()).build());
            responseObserver.onCompleted();
          }
        };

    Server server = ServerBuilder.forPort(0).addService(greeter).build().start();
    NameResolver resolver =
        mock(
            NameResolver.class,
            invocation -> {
              switch (invocation.getMethod().getName()) {
                case "getServiceAuthority":
                  return "localhost";
                case "start":
                  NameResolver.Listener listener = invocation.getArgument(0);
                  listener.onAddresses(
                      singletonList(
                          new EquivalentAddressGroup(
                              new InetSocketAddress("localhost", server.getPort()))),
                      Attributes.EMPTY);
                  return null;
                default:
                  return null;
              }
            });
    NameResolver.Factory resolverFactory =
        mock(
            NameResolver.Factory.class,
            invocation -> {
              switch (invocation.getMethod().getName()) {
                case "getDefaultScheme":
                  return "consul";
                case "newNameResolver":
                  return resolver;
                default:
                  return null;
              }
            });
    ManagedChannelBuilder<?> channelBuilder =
        ManagedChannelBuilder.forTarget("consul:1234")
            .nameResolverFactory(resolverFactory)
            .overrideAuthority("fallback.invalid:1234");
    configureClient(channelBuilder);
    usePlainText(channelBuilder);
    ManagedChannel channel = channelBuilder.build();

    closer.add(() -> channel.shutdownNow().awaitTermination(10, SECONDS));
    closer.add(() -> server.shutdownNow().awaitTermination());

    GreeterGrpc.GreeterBlockingStub client = GreeterGrpc.newBlockingStub(channel);
    testing.runWithSpan(
        "parent", () -> client.sayHello(Helloworld.Request.newBuilder().setName("test").build()));

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName("example.Greeter/SayHello")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttribute(SERVER_ADDRESS, "consul:1234")
                        .hasAttributesSatisfying(equalTo(SERVER_PORT, null)),
                span ->
                    span.hasName("example.Greeter/SayHello")
                        .hasKind(SpanKind.SERVER)
                        .hasParent(trace.getSpan(1))));
  }

  private static void usePlainText(ManagedChannelBuilder<?> channelBuilder) throws Exception {
    try {
      channelBuilder
          .getClass()
          .getMethod("usePlaintext", boolean.class)
          .invoke(channelBuilder, true);
    } catch (NoSuchMethodException ignored) {
      channelBuilder.getClass().getMethod("usePlaintext").invoke(channelBuilder);
    }
  }
}
