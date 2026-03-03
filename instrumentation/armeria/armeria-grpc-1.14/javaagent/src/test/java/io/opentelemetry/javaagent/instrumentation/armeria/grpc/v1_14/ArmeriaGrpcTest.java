/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.armeria.grpc.v1_14;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldRpcSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableRpcSemconv;
import static io.opentelemetry.instrumentation.testing.junit.rpc.SemconvRpcStabilityUtil.maybeStable;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.ClientAttributes.CLIENT_ADDRESS;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_REQUEST_METHOD;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_RESPONSE_STATUS_CODE;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_ROUTE;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PROTOCOL_VERSION;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.UrlAttributes.URL_PATH;
import static io.opentelemetry.semconv.UrlAttributes.URL_SCHEME;
import static io.opentelemetry.semconv.UserAgentAttributes.USER_AGENT_ORIGINAL;
import static io.opentelemetry.semconv.incubating.MessageIncubatingAttributes.MESSAGE_ID;
import static io.opentelemetry.semconv.incubating.MessageIncubatingAttributes.MESSAGE_TYPE;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_GRPC_STATUS_CODE;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_METHOD;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_RESPONSE_STATUS_CODE;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SERVICE;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SYSTEM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.linecorp.armeria.client.grpc.GrpcClients;
import com.linecorp.armeria.server.ServerBuilder;
import com.linecorp.armeria.server.grpc.GrpcService;
import com.linecorp.armeria.testing.junit5.server.ServerExtension;
import example.GreeterGrpc;
import example.Helloworld;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.protobuf.services.HealthStatusManager;
import io.grpc.stub.StreamObserver;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.sdk.trace.data.StatusData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class ArmeriaGrpcTest {

  @RegisterExtension
  static final AgentInstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension
  static final ServerExtension server =
      new ServerExtension() {
        @Override
        protected void configure(ServerBuilder sb) throws Exception {
          sb.service(
              GrpcService.builder()
                  .addService(
                      new GreeterGrpc.GreeterImplBase() {
                        @Override
                        public void sayHello(
                            Helloworld.Request request,
                            StreamObserver<Helloworld.Response> responseObserver) {
                          responseObserver.onNext(
                              Helloworld.Response.newBuilder()
                                  .setMessage("Hello " + request.getName())
                                  .build());
                          responseObserver.onCompleted();
                        }
                      })
                  .build());
        }
      };

  @RegisterExtension
  static final ServerExtension serverWithoutGreeter =
      new ServerExtension() {
        @Override
        protected void configure(ServerBuilder sb) {
          sb.service(
              GrpcService.builder()
                  .addService(new HealthStatusManager().getHealthService())
                  .build());
        }
      };

  @SuppressWarnings("deprecation") // using deprecated semconv
  @Test
  void grpcInstrumentation() {
    GreeterGrpc.GreeterBlockingStub client =
        GrpcClients.builder(server.httpUri()).build(GreeterGrpc.GreeterBlockingStub.class);

    Helloworld.Request request = Helloworld.Request.newBuilder().setName("test").build();
    Helloworld.Response response = testing.runWithSpan("parent", () -> client.sayHello(request));

    assertThat(response.getMessage()).isEqualTo("Hello test");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName("example.Greeter/SayHello")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(RPC_SYSTEM), "grpc"),
                            equalTo(RPC_SERVICE, emitOldRpcSemconv() ? "example.Greeter" : null),
                            equalTo(
                                RPC_METHOD,
                                emitStableRpcSemconv() ? "example.Greeter/SayHello" : "SayHello"),
                            equalTo(
                                RPC_GRPC_STATUS_CODE,
                                emitOldRpcSemconv() ? (long) Status.Code.OK.value() : null),
                            equalTo(
                                RPC_RESPONSE_STATUS_CODE,
                                emitStableRpcSemconv() ? Status.Code.OK.name() : null),
                            equalTo(SERVER_ADDRESS, "127.0.0.1"),
                            equalTo(SERVER_PORT, (long) server.httpPort()))
                        .hasEventsSatisfyingExactly(
                            event ->
                                event
                                    .hasName("message")
                                    .hasAttributesSatisfyingExactly(
                                        equalTo(MESSAGE_TYPE, "SENT"), equalTo(MESSAGE_ID, 1L)),
                            event ->
                                event
                                    .hasName("message")
                                    .hasAttributesSatisfyingExactly(
                                        equalTo(MESSAGE_TYPE, "RECEIVED"),
                                        equalTo(MESSAGE_ID, 1L))),
                span ->
                    span.hasName("POST /example.Greeter/SayHello")
                        .hasKind(SpanKind.SERVER)
                        .hasParent(trace.getSpan(1))
                        .hasAttributesSatisfyingExactly(
                            equalTo(HTTP_REQUEST_METHOD, "POST"),
                            equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
                            equalTo(HTTP_ROUTE, "/example.Greeter/SayHello"),
                            equalTo(URL_PATH, "/example.Greeter/SayHello"),
                            equalTo(URL_SCHEME, "http"),
                            equalTo(SERVER_ADDRESS, "127.0.0.1"),
                            equalTo(SERVER_PORT, (long) server.httpPort()),
                            equalTo(CLIENT_ADDRESS, "127.0.0.1"),
                            equalTo(NETWORK_PEER_ADDRESS, "127.0.0.1"),
                            satisfies(NETWORK_PEER_PORT, port -> port.isInstanceOf(Long.class)),
                            equalTo(NETWORK_PROTOCOL_VERSION, "2"),
                            satisfies(USER_AGENT_ORIGINAL, val -> val.startsWith("armeria/"))),
                span ->
                    span.hasName("example.Greeter/SayHello")
                        .hasKind(SpanKind.SERVER)
                        .hasParent(trace.getSpan(1))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(RPC_SYSTEM), "grpc"),
                            equalTo(RPC_SERVICE, emitOldRpcSemconv() ? "example.Greeter" : null),
                            equalTo(
                                RPC_METHOD,
                                emitStableRpcSemconv() ? "example.Greeter/SayHello" : "SayHello"),
                            equalTo(
                                RPC_GRPC_STATUS_CODE,
                                emitOldRpcSemconv() ? (long) Status.Code.OK.value() : null),
                            equalTo(
                                RPC_RESPONSE_STATUS_CODE,
                                emitStableRpcSemconv() ? Status.Code.OK.name() : null),
                            equalTo(SERVER_ADDRESS, "127.0.0.1"),
                            equalTo(SERVER_PORT, server.httpPort()))
                        .hasEventsSatisfyingExactly(
                            event ->
                                event
                                    .hasName("message")
                                    .hasAttributesSatisfyingExactly(
                                        equalTo(MESSAGE_TYPE, "RECEIVED"), equalTo(MESSAGE_ID, 1L)),
                            event ->
                                event
                                    .hasName("message")
                                    .hasAttributesSatisfyingExactly(
                                        equalTo(MESSAGE_TYPE, "SENT"), equalTo(MESSAGE_ID, 1L)))));
  }

  @SuppressWarnings("deprecation") // using deprecated semconv
  @Test
  void unknownService() {
    GreeterGrpc.GreeterBlockingStub client =
        GrpcClients.builder(serverWithoutGreeter.httpUri())
            .build(GreeterGrpc.GreeterBlockingStub.class);

    Helloworld.Request request = Helloworld.Request.newBuilder().setName("test").build();
    assertThatThrownBy(() -> client.sayHello(request)).isInstanceOf(StatusRuntimeException.class);

    // Armeria uses exact-route binding for gRPC services, so calling an unregistered service
    // results in an HTTP-level response rather than gRPC UNIMPLEMENTED. The armeria HTTP
    // instrumentation captures this as an HTTP server span.
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("example.Greeter/SayHello")
                        .hasKind(SpanKind.CLIENT)
                        .hasNoParent()
                        .hasStatus(StatusData.error())
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(RPC_SYSTEM), "grpc"),
                            equalTo(RPC_SERVICE, emitOldRpcSemconv() ? "example.Greeter" : null),
                            equalTo(
                                RPC_METHOD,
                                emitStableRpcSemconv() ? "example.Greeter/SayHello" : "SayHello"),
                            equalTo(
                                RPC_GRPC_STATUS_CODE,
                                emitOldRpcSemconv()
                                    ? (long) Status.Code.UNIMPLEMENTED.value()
                                    : null),
                            equalTo(
                                RPC_RESPONSE_STATUS_CODE,
                                emitStableRpcSemconv() ? Status.Code.UNIMPLEMENTED.name() : null),
                            equalTo(SERVER_ADDRESS, "127.0.0.1"),
                            equalTo(SERVER_PORT, (long) serverWithoutGreeter.httpPort()))
                        .hasEventsSatisfyingExactly(
                            event ->
                                event
                                    .hasName("message")
                                    .hasAttributesSatisfyingExactly(
                                        equalTo(MESSAGE_TYPE, "SENT"), equalTo(MESSAGE_ID, 1L))),
                span ->
                    span.hasName("POST /*")
                        .hasKind(SpanKind.SERVER)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(HTTP_REQUEST_METHOD, "POST"),
                            equalTo(HTTP_RESPONSE_STATUS_CODE, 404),
                            equalTo(HTTP_ROUTE, "/*"),
                            equalTo(URL_PATH, "/example.Greeter/SayHello"),
                            equalTo(URL_SCHEME, "http"),
                            equalTo(SERVER_ADDRESS, "127.0.0.1"),
                            equalTo(SERVER_PORT, (long) serverWithoutGreeter.httpPort()),
                            equalTo(CLIENT_ADDRESS, "127.0.0.1"),
                            equalTo(NETWORK_PEER_ADDRESS, "127.0.0.1"),
                            satisfies(NETWORK_PEER_PORT, port -> port.isInstanceOf(Long.class)),
                            equalTo(NETWORK_PROTOCOL_VERSION, "2"),
                            satisfies(USER_AGENT_ORIGINAL, val -> val.startsWith("armeria/")))));
  }
}
