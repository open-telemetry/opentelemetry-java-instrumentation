/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_6;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.instrumentation.testing.util.TestLatestDeps.testLatestDeps;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import example.GreeterGrpc;
import example.Helloworld;
import io.grpc.BindableService;
import io.grpc.CallOptions;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.grpc.v1_6.internal.Experimental;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class GrpcTest extends AbstractGrpcTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  private static final Metadata.Key<String> CUSTOM_METADATA_KEY =
      Metadata.Key.of("customMetadataKey", Metadata.ASCII_STRING_MARSHALLER);

  @Override
  protected ServerBuilder<?> configureServer(ServerBuilder<?> server) {
    GrpcTelemetry telemetry =
        GrpcTelemetry.builder(testing.getOpenTelemetry())
            .setServerRequestHeaders(
                IncludeExclude.builder()
                    .setIncluded(singletonList(SERVER_REQUEST_HEADER_KEY))
                    .build())
            .build();
    telemetry.configureServerBuilder(server);
    // A server can be configured more than once.
    telemetry.configureServerBuilder(server);
    return server;
  }

  @Override
  protected ManagedChannelBuilder<?> configureClient(ManagedChannelBuilder<?> client) {
    Experimental.addClientInterceptor(
        GrpcTelemetry.builder(testing.getOpenTelemetry())
            .setClientRequestHeaders(
                IncludeExclude.builder()
                    .setIncluded(singletonList(CLIENT_REQUEST_HEADER_KEY))
                    .build())
            .build(),
        client);
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
  void responseHeadersPreserveCallerContext() throws Exception {
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
    Server server = configureServer(ServerBuilder.forPort(0).addService(greeter)).build().start();
    closer.add(() -> server.shutdownNow().awaitTermination());
    ManagedChannel channel =
        createChannel(
            configureClient(ManagedChannelBuilder.forAddress("localhost", server.getPort())));
    closer.add(() -> channel.shutdownNow().awaitTermination(10, SECONDS));

    ContextKey<String> key = ContextKey.named("custom-value");
    AtomicReference<Span> callerSpan = new AtomicReference<>();
    ClientCall<Helloworld.Request, Helloworld.Response> call =
        testing.runWithSpan(
            "parent",
            () -> {
              callerSpan.set(Span.current());
              try (Scope ignored = Context.current().with(key, "caller-value").makeCurrent()) {
                return channel.newCall(GreeterGrpc.METHOD_SAY_HELLO, CallOptions.DEFAULT);
              }
            });

    AtomicReference<Context> headersContext = new AtomicReference<>();
    AtomicReference<Helloworld.Response> response = new AtomicReference<>();
    AtomicReference<Status> status = new AtomicReference<>();
    CountDownLatch closed = new CountDownLatch(1);
    call.start(
        new ClientCall.Listener<Helloworld.Response>() {
          @Override
          public void onHeaders(Metadata headers) {
            headersContext.set(Context.current());
            testing.runWithSpan("onHeaders", () -> {});
          }

          @Override
          public void onMessage(Helloworld.Response message) {
            response.set(message);
          }

          @Override
          public void onClose(Status callStatus, Metadata trailers) {
            status.set(callStatus);
            closed.countDown();
          }
        },
        new Metadata());
    call.request(1);
    call.sendMessage(Helloworld.Request.newBuilder().setName("test").build());
    call.halfClose();

    assertThat(closed.await(10, SECONDS)).isTrue();
    assertThat(status.get().isOk()).isTrue();
    assertThat(response.get().getMessage()).isEqualTo("Hello test");
    assertThat(headersContext.get()).isNotNull();
    assertThat(Span.fromContext(headersContext.get())).isSameAs(callerSpan.get());
    assertThat(headersContext.get().get(key)).isEqualTo("caller-value");

    testing.waitAndAssertTraces(trace ->
        trace.hasSpansSatisfyingExactly(
            span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
            span ->
                span.hasName("example.Greeter/SayHello")
                    .hasKind(SpanKind.CLIENT)
                    .hasParent(trace.getSpan(0)),
            span ->
                span.hasName("example.Greeter/SayHello")
                    .hasKind(SpanKind.SERVER)
                    .hasParent(trace.getSpan(1)),
            span -> span.hasName("onHeaders").hasParent(trace.getSpan(0))));
  }

  @Test
  void registeredMethodShortCircuitedBeforeTracingInterceptor() throws Exception {
    ServerBuilder<?> serverBuilder =
        ServerBuilder.forPort(0).addService(new GreeterGrpc.GreeterImplBase() {});
    GrpcTelemetry.create(testing.getOpenTelemetry()).configureServerBuilder(serverBuilder);
    serverBuilder.intercept(
        new ServerInterceptor() {
          @Override
          public <REQUEST, RESPONSE> ServerCall.Listener<REQUEST> interceptCall(
              ServerCall<REQUEST, RESPONSE> call,
              Metadata headers,
              ServerCallHandler<REQUEST, RESPONSE> next) {
            call.close(
                Status.UNIMPLEMENTED.withDescription(
                    "Method not found: " + call.getMethodDescriptor().getFullMethodName()),
                new Metadata());
            return new ServerCall.Listener<REQUEST>() {};
          }
        });
    Server server = serverBuilder.build().start();
    ManagedChannel channel =
        createChannel(
            configureClient(ManagedChannelBuilder.forAddress("localhost", server.getPort())));
    closer.add(() -> channel.shutdownNow().awaitTermination(10, SECONDS));
    closer.add(() -> server.shutdownNow().awaitTermination());

    GreeterGrpc.GreeterBlockingStub client = GreeterGrpc.newBlockingStub(channel);
    Helloworld.Request request = Helloworld.Request.newBuilder().setName("test").build();

    assertThatThrownBy(() -> client.sayHello(request)).isInstanceOf(StatusRuntimeException.class);

    testing.waitAndAssertTraces(
        trace -> trace.hasSpansSatisfyingExactly(span -> span.hasKind(SpanKind.CLIENT)));
  }

  @SuppressWarnings("unchecked")
  @Test
  void nonMethodUnimplementedStatusDoesNotStartSpan() {
    Instrumenter<GrpcRequest, Status> instrumenter = mock(Instrumenter.class);
    TracingServerStreamTracer tracer =
        new TracingServerStreamTracer(
            instrumenter,
            ContextPropagators.noop(),
            "example.Greeter/SayHello",
            new Metadata(),
            Context.root());

    tracer.streamClosed(
        Status.UNIMPLEMENTED.withDescription("Can't find decompressor for unsupported"));

    verifyNoInteractions(instrumenter);
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", "dns:///"})
  void clientAddressFromTargetOrAuthority(String targetPrefix) throws Exception {
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
    Server server = configureServer(ServerBuilder.forPort(0).addService(greeter)).build().start();
    closer.add(() -> server.shutdownNow().awaitTermination());

    GrpcTelemetry telemetry = GrpcTelemetry.create(testing.getOpenTelemetry());
    String target =
        (targetPrefix == null ? "dns:///" : targetPrefix) + "localhost:" + server.getPort();
    ManagedChannel channel =
        createChannel(
            ManagedChannelBuilder.forTarget(target)
                .overrideAuthority("fallback.invalid:1234")
                .intercept(
                    targetPrefix == null
                        ? telemetry.createClientInterceptor()
                        : telemetry.createClientInterceptor(target)));
    closer.add(() -> channel.shutdownNow().awaitTermination(10, SECONDS));

    Helloworld.Response response =
        testing.runWithSpan(
            "parent",
            () ->
                GreeterGrpc.newBlockingStub(channel)
                    .sayHello(Helloworld.Request.newBuilder().setName("test").build()));

    assertThat(response.getMessage()).isEqualTo("Hello test");
    testing.waitAndAssertTraces(trace ->
        trace.hasSpansSatisfyingExactly(
            span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
            span ->
                span.hasName("example.Greeter/SayHello")
                    .hasKind(SpanKind.CLIENT)
                    .hasParent(trace.getSpan(0))
                    .hasAttribute(
                        SERVER_ADDRESS, targetPrefix == null ? "fallback.invalid" : "localhost")
                    .hasAttribute(SERVER_PORT, targetPrefix == null ? 1234L : server.getPort()),
            span ->
                span.hasName("example.Greeter/SayHello")
                    .hasKind(SpanKind.SERVER)
                    .hasParent(trace.getSpan(1))));
  }

  @Test
  void automaticTargetCaptureFallsBackForUnsupportedBuilder() {
    ManagedChannelBuilder<?> builder = mock(ManagedChannelBuilder.class, CALLS_REAL_METHODS);

    Experimental.addClientInterceptor(GrpcTelemetry.create(testing.getOpenTelemetry()), builder);

    verify(builder).intercept(isA(ClientInterceptor.class));
  }

  /**
   * metadataProvided. testing as well 2 extra methods: {@link
   * GrpcTelemetryBuilder#addClientAttributeExtractor} and {@link
   * GrpcTelemetryBuilder#addServerAttributeExtractor}
   */
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

    GrpcTelemetry serverTelemetry =
        GrpcTelemetry.builder(testing.getOpenTelemetry())
            .addAttributesExtractor(new CustomAttributesExtractor())
            .addServerAttributeExtractor(new CustomAttributesExtractorV2("serverSideValue"))
            .build();
    ServerBuilder<?> serverBuilder = ServerBuilder.forPort(0).addService(greeter);
    serverTelemetry.configureServerBuilder(serverBuilder);
    Server server = serverBuilder.build().start();

    ManagedChannelBuilder<?> channelBuilder =
        ManagedChannelBuilder.forAddress("localhost", server.getPort());
    channelBuilder.intercept(
        GrpcTelemetry.builder(testing.getOpenTelemetry())
            .addAttributesExtractor(new CustomAttributesExtractor())
            .addClientAttributeExtractor(new CustomAttributesExtractorV2("clientSideValue"))
            .build()
            .createClientInterceptor());
    ManagedChannel channel = createChannel(channelBuilder);

    closer.add(() -> channel.shutdownNow().awaitTermination(10, SECONDS));
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

    assertThat(response.getMessage()).isEqualTo("Hello test");

    testing()
        .waitAndAssertTraces(trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName("example.Greeter/SayHello")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttribute(stringKey("customKey2"), "clientSideValue")
                        .hasAttribute(stringKey("customKey"), "customValue"),
                span ->
                    span.hasName("example.Greeter/SayHello")
                        .hasKind(SpanKind.SERVER)
                        .hasParent(trace.getSpan(1))
                        .hasAttribute(stringKey("customKey2"), "serverSideValue")
                        .hasAttribute(stringKey("customKey"), "customValue")));
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
        Status status,
        Throwable error) {

      Metadata metadata = grpcRequest.getMetadata();
      if (metadata != null) {
        attributes.put(stringKey("customKey"), metadata.get(CUSTOM_METADATA_KEY));
      }
    }
  }

  private static class CustomAttributesExtractorV2
      implements AttributesExtractor<GrpcRequest, Status> {

    private final String valueOfKey2;

    CustomAttributesExtractorV2(String valueOfKey2) {
      this.valueOfKey2 = valueOfKey2;
    }

    @Override
    public void onStart(
        AttributesBuilder attributes, Context parentContext, GrpcRequest grpcRequest) {

      attributes.put(stringKey("customKey2"), valueOfKey2);
    }

    @Override
    public void onEnd(
        AttributesBuilder attributes,
        Context context,
        GrpcRequest grpcRequest,
        Status status,
        Throwable error) {}
  }
}
