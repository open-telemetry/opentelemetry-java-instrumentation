/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_6;

import example.GreeterGrpc;
import example.Helloworld;
import io.grpc.BindableService;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.Status;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import javax.annotation.Nullable;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

class GrpcTest extends AbstractGrpcTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  private static final String customKey = "customKey";
  private static final Metadata.Key<String> customMetadataKey =
      Metadata.Key.of(customKey, Metadata.ASCII_STRING_MARSHALLER);
  private static final AttributesExtractor<GrpcRequest, Status> customMetadataExtractor =
      new AttributesExtractor<GrpcRequest, Status>() {
        @Override
        public void onStart(AttributesBuilder attributes, Context parentContext,
            GrpcRequest grpcRequest) {}

        @Override
        public void onEnd(AttributesBuilder attributes, Context context,
            GrpcRequest grpcRequest, @Nullable Status status,
            @Nullable Throwable error) {
          Metadata metadata = grpcRequest.getMetadata();
          if (metadata != null && metadata.containsKey(customMetadataKey)) {
            String value = metadata.get(customMetadataKey);
            if (value != null) {
              attributes.put(customKey, value);
            }
          }
        }
      };

  @Override
  protected ServerBuilder<?> configureServer(ServerBuilder<?> server) {
    return configureServer(server, Function.identity());
  }

  protected ServerBuilder<?> configureServer(ServerBuilder<?> server,
      Function<? super GrpcTelemetryBuilder, ? extends GrpcTelemetryBuilder> customizer) {
    GrpcTelemetryBuilder builder =
        customizer.apply(GrpcTelemetry.builder(testing.getOpenTelemetry()));
    return server.intercept(builder.build().newServerInterceptor());
  }

  @Override
  protected ManagedChannelBuilder<?> configureClient(ManagedChannelBuilder<?> client) {
    return configureClient(client, Function.identity());
  }

  protected ManagedChannelBuilder<?> configureClient(ManagedChannelBuilder<?> client,
      Function<? super GrpcTelemetryBuilder, ? extends GrpcTelemetryBuilder> customizer) {
    GrpcTelemetryBuilder builder =
        customizer.apply(GrpcTelemetry.builder(testing.getOpenTelemetry()));
    return client.intercept(builder.build().newClientInterceptor());
  }

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  @Test
  void metadataProvided() throws Exception {
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
    Server server = configureServer(
        ServerBuilder.forPort(0).addService(greeter),
        customizer -> customizer.addAttributeExtractor(customMetadataExtractor))
        .build()
        .start();

    ManagedChannel channel =
        createChannel(
            configureClient(
                ManagedChannelBuilder.forAddress("localhost", server.getPort()),
                customizer -> customizer.addAttributeExtractor(customMetadataExtractor))
        );

    closer().add(() -> channel.shutdownNow().awaitTermination(10, TimeUnit.SECONDS));
    closer().add(() -> server.shutdownNow().awaitTermination());

    Metadata extraMetadata = new Metadata();
    extraMetadata.put(customMetadataKey, "customValue");

    GreeterGrpc.GreeterBlockingStub client = GreeterGrpc
        .newBlockingStub(channel)
        .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(extraMetadata));

    Helloworld.Response response =
        testing()
            .runWithSpan(
                "parent",
                () -> client.sayHello(Helloworld.Request.newBuilder().setName("test").build()));

    OpenTelemetryAssertions.assertThat(response.getMessage()).isEqualTo("Hello test");

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                    span ->
                        span.hasName("example.Greeter/SayHello")
                            .hasKind(SpanKind.CLIENT)
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfying(
                                attrs ->
                                    OpenTelemetryAssertions.assertThat(attrs)
                                        .containsEntry(customKey, "customValue")),
                    span ->
                        span.hasName("example.Greeter/SayHello")
                            .hasKind(SpanKind.SERVER)
                            .hasParent(trace.getSpan(1))
                            .hasAttributesSatisfying(
                                attrs ->
                                    OpenTelemetryAssertions.assertThat(attrs)
                                        .containsEntry(customKey, "customValue"))
                )
        );
  }

}
