/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.armeria.grpc.v1_14;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldRpcSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableRpcSemconv;
import static io.opentelemetry.instrumentation.testing.junit.rpc.SemconvRpcStabilityUtil.maybeStable;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.incubating.MessageIncubatingAttributes.MESSAGE_ID;
import static io.opentelemetry.semconv.incubating.MessageIncubatingAttributes.MESSAGE_TYPE;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_GRPC_STATUS_CODE;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_METHOD;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_RESPONSE_STATUS_CODE;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SERVICE;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SYSTEM;
import static org.assertj.core.api.Assertions.assertThat;

import com.linecorp.armeria.client.grpc.GrpcClients;
import com.linecorp.armeria.server.ServerBuilder;
import com.linecorp.armeria.server.grpc.GrpcService;
import com.linecorp.armeria.testing.junit5.server.ServerExtension;
import example.GreeterGrpc;
import example.Helloworld;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
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
                                emitStableRpcSemconv()
                                    ? String.valueOf(Status.Code.OK.value())
                                    : null),
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
                                emitStableRpcSemconv()
                                    ? String.valueOf(Status.Code.OK.value())
                                    : null),
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
}
