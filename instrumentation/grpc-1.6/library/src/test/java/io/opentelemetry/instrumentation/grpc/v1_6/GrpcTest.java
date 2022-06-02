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
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class GrpcTest extends AbstractGrpcTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  private static final AttributeKey<String> CUSTOM_KEY = AttributeKey.stringKey("customKey");

  private static final Metadata.Key<String> CUSTOM_METADATA_KEY =
      Metadata.Key.of("customMetadataKey", Metadata.ASCII_STRING_MARSHALLER);

  @Override
  protected ServerBuilder<?> configureServer(ServerBuilder<?> server) {
    return server.intercept(
        GrpcTelemetry.builder(testing.getOpenTelemetry()).build().newServerInterceptor());
  }

  @Override
  protected ManagedChannelBuilder<?> configureClient(ManagedChannelBuilder<?> client) {
    return client.intercept(
        GrpcTelemetry.builder(testing.getOpenTelemetry()).build().newClientInterceptor());
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

    Server server =
        ServerBuilder.forPort(0)
            .addService(greeter)
            .intercept(
                GrpcTelemetry.builder(testing.getOpenTelemetry())
                    .addAttributeExtractor(new CustomAttributesExtractor())
                    .build()
                    .newServerInterceptor())
            .build()
            .start();

    ManagedChannel channel =
        createChannel(
            ManagedChannelBuilder.forAddress("localhost", server.getPort())
                .intercept(
                    GrpcTelemetry.builder(testing.getOpenTelemetry())
                        .addAttributeExtractor(new CustomAttributesExtractor())
                        .build()
                        .newClientInterceptor()));

    closer.add(() -> channel.shutdownNow().awaitTermination(10, TimeUnit.SECONDS));
    closer.add(() -> server.shutdownNow().awaitTermination());

    Metadata extraMetadata = new Metadata();
    extraMetadata.put(CUSTOM_METADATA_KEY, "customValue");

    GreeterGrpc.GreeterBlockingStub client =
        GreeterGrpc.newBlockingStub(channel)
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
                            .hasAttribute(CUSTOM_KEY, "customValue"),
                    span ->
                        span.hasName("example.Greeter/SayHello")
                            .hasKind(SpanKind.SERVER)
                            .hasParent(trace.getSpan(1))
                            .hasAttribute(CUSTOM_KEY, "customValue")));
  }

  private static class CustomAttributesExtractor
      implements AttributesExtractor<GrpcRequest, Status> {

    @Override
    public void onStart(
        AttributesBuilder attributes, Context parentContext, GrpcRequest grpcRequest) {}

    @Override
    public void onEnd(
        AttributesBuilder attributes,
        Context context,
        GrpcRequest grpcRequest,
        @Nullable Status status,
        @Nullable Throwable error) {

      Metadata metadata = grpcRequest.getMetadata();
      if (metadata != null && metadata.containsKey(CUSTOM_METADATA_KEY)) {
        String value = metadata.get(CUSTOM_METADATA_KEY);
        if (value != null) {
          attributes.put(CUSTOM_KEY, value);
        }
      }
    }
  }
}
