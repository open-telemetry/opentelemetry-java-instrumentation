/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import example.GreeterGrpc
import example.Helloworld
import io.grpc.BindableService
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.Server
import io.grpc.ServerBuilder
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.stub.StreamObserver
import io.opentelemetry.auto.common.exec.CommonTaskExecutor
import io.opentelemetry.auto.instrumentation.grpc.common.GrpcHelper
import io.opentelemetry.auto.test.AgentTestRunner
import io.opentelemetry.auto.test.utils.PortUtils
import io.opentelemetry.trace.attributes.SemanticAttributes

import java.util.concurrent.TimeUnit

import static io.opentelemetry.auto.test.utils.TraceUtils.basicSpan
import static io.opentelemetry.auto.test.utils.TraceUtils.runUnderTrace
import static io.opentelemetry.trace.Span.Kind.CLIENT
import static io.opentelemetry.trace.Span.Kind.SERVER

class GrpcTest extends AgentTestRunner {

  def "test request-response"() {
    setup:
    BindableService greeter = new GreeterGrpc.GreeterImplBase() {
      @Override
      void sayHello(
        final Helloworld.Request req, final StreamObserver<Helloworld.Response> responseObserver) {
        final Helloworld.Response reply = Helloworld.Response.newBuilder().setMessage("Hello $req.name").build()
        CommonTaskExecutor.INSTANCE.execute {
          if (!testTracer.getCurrentSpan().getContext().isValid()) {
            responseObserver.onError(new IllegalStateException("no active span"))
          } else {
            responseObserver.onNext(reply)
            responseObserver.onCompleted()
          }
        }
      }
    }
    def port = PortUtils.randomOpenPort()
    Server server = ServerBuilder.forPort(port).addService(greeter).build().start()
    ManagedChannelBuilder channelBuilder = ManagedChannelBuilder.forAddress("localhost", port)

    // Depending on the version of gRPC usePlainText may or may not take an argument.
    try {
      channelBuilder.usePlaintext()
    } catch (MissingMethodException e) {
      channelBuilder.usePlaintext(true)
    }
    ManagedChannel channel = channelBuilder.build()
    GreeterGrpc.GreeterBlockingStub client = GreeterGrpc.newBlockingStub(channel)

    when:
    def response = runUnderTrace("parent") {
      client.sayHello(Helloworld.Request.newBuilder().setName(name).build())
    }

    then:
    response.message == "Hello $name"

    assertTraces(1) {
      trace(0, 3) {
        basicSpan(it, 0, "parent")
        span(1) {
          operationName "example.Greeter/SayHello"
          spanKind CLIENT
          childOf span(0)
          errored false
          status(io.opentelemetry.trace.Status.OK)
          event(0) {
            eventName "message"
            attributes {
              "message.type" "SENT"
              "message.id" 1
            }
          }
          attributes {
            "${SemanticAttributes.RPC_SERVICE.key()}" "Greeter"
            "${SemanticAttributes.NET_PEER_NAME.key()}" "localhost"
            "${SemanticAttributes.NET_PEER_PORT.key()}" port
          }
        }
        span(2) {
          operationName "example.Greeter/SayHello"
          spanKind SERVER
          childOf span(1)
          errored false
          status(io.opentelemetry.trace.Status.OK)
          event(0) {
            eventName "message"
            attributes {
              "message.type" "RECEIVED"
              "message.id" 1
            }
          }
          attributes {
            "${SemanticAttributes.RPC_SERVICE.key()}" "Greeter"
            "${SemanticAttributes.NET_PEER_IP.key()}" "127.0.0.1"
            "${SemanticAttributes.NET_PEER_PORT.key()}" Long
          }
        }
      }
    }

    cleanup:
    channel?.shutdownNow()?.awaitTermination(10, TimeUnit.SECONDS)
    server?.shutdownNow()?.awaitTermination()

    where:
    name << ["some name", "some other name"]
  }

  def "test error - #name"() {
    setup:
    def error = grpcStatus.asException()
    BindableService greeter = new GreeterGrpc.GreeterImplBase() {
      @Override
      void sayHello(
        final Helloworld.Request req, final StreamObserver<Helloworld.Response> responseObserver) {
        responseObserver.onError(error)
      }
    }
    def port = PortUtils.randomOpenPort()
    Server server = ServerBuilder.forPort(port).addService(greeter).build().start()
    ManagedChannelBuilder channelBuilder = ManagedChannelBuilder.forAddress("localhost", port)

    // Depending on the version of gRPC usePlainText may or may not take an argument.
    try {
      channelBuilder.usePlaintext()
    } catch (MissingMethodException e) {
      channelBuilder.usePlaintext(true)
    }
    ManagedChannel channel = channelBuilder.build()
    GreeterGrpc.GreeterBlockingStub client = GreeterGrpc.newBlockingStub(channel)

    when:
    client.sayHello(Helloworld.Request.newBuilder().setName(name).build())

    then:
    thrown StatusRuntimeException

    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          operationName "example.Greeter/SayHello"
          spanKind CLIENT
          parent()
          errored true
          status(GrpcHelper.statusFromGrpcStatus(grpcStatus))
          attributes {
            "${SemanticAttributes.RPC_SERVICE.key()}" "Greeter"
            "${SemanticAttributes.NET_PEER_NAME.key()}" "localhost"
            "${SemanticAttributes.NET_PEER_PORT.key()}" port
          }
        }
        span(1) {
          operationName "example.Greeter/SayHello"
          spanKind SERVER
          childOf span(0)
          errored true
          status(GrpcHelper.statusFromGrpcStatus(grpcStatus))
          event(0) {
            eventName "message"
            attributes {
              "message.type" "RECEIVED"
              "message.id" 1
            }
          }
          if (grpcStatus.cause != null) {
            errorEvent grpcStatus.cause.class, grpcStatus.cause.message, 1
          }
          attributes {
            "${SemanticAttributes.RPC_SERVICE.key()}" "Greeter"
            "${SemanticAttributes.NET_PEER_IP.key()}" "127.0.0.1"
            "${SemanticAttributes.NET_PEER_PORT.key()}" Long
          }
        }
      }
    }

    cleanup:
    channel?.shutdownNow()?.awaitTermination(10, TimeUnit.SECONDS)
    server?.shutdownNow()?.awaitTermination()

    where:
    name                          | grpcStatus
    "Runtime - cause"             | Status.UNKNOWN.withCause(new RuntimeException("some error"))
    "Status - cause"              | Status.PERMISSION_DENIED.withCause(new RuntimeException("some error"))
    "StatusRuntime - cause"       | Status.UNIMPLEMENTED.withCause(new RuntimeException("some error"))
    "Runtime - description"       | Status.UNKNOWN.withDescription("some description")
    "Status - description"        | Status.PERMISSION_DENIED.withDescription("some description")
    "StatusRuntime - description" | Status.UNIMPLEMENTED.withDescription("some description")
  }

  def "test error thrown - #name"() {
    setup:
    def error = grpcStatus.asRuntimeException()
    BindableService greeter = new GreeterGrpc.GreeterImplBase() {
      @Override
      void sayHello(
        final Helloworld.Request req, final StreamObserver<Helloworld.Response> responseObserver) {
        throw error
      }
    }
    def port = PortUtils.randomOpenPort()
    Server server = ServerBuilder.forPort(port).addService(greeter).build().start()
    ManagedChannelBuilder channelBuilder = ManagedChannelBuilder.forAddress("localhost", port)

    // Depending on the version of gRPC usePlainText may or may not take an argument.
    try {
      channelBuilder.usePlaintext()
    } catch (MissingMethodException e) {
      channelBuilder.usePlaintext(true)
    }
    ManagedChannel channel = channelBuilder.build()
    GreeterGrpc.GreeterBlockingStub client = GreeterGrpc.newBlockingStub(channel)

    when:
    client.sayHello(Helloworld.Request.newBuilder().setName(name).build())

    then:
    thrown StatusRuntimeException

    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          operationName "example.Greeter/SayHello"
          spanKind CLIENT
          parent()
          errored true
          // NB: Exceptions thrown on the server don't appear to be propagated to the client, at
          // least for the version we test against.
          status(io.opentelemetry.trace.Status.UNKNOWN)
          attributes {
            "${SemanticAttributes.RPC_SERVICE.key()}" "Greeter"
            "${SemanticAttributes.NET_PEER_NAME.key()}" "localhost"
            "${SemanticAttributes.NET_PEER_PORT.key()}" Long
          }
        }
        span(1) {
          operationName "example.Greeter/SayHello"
          spanKind SERVER
          childOf span(0)
          errored true
          status(GrpcHelper.statusFromGrpcStatus(grpcStatus))
          event(0) {
            eventName "message"
            attributes {
              "message.type" "RECEIVED"
              "message.id" 1
            }
          }
          if (grpcStatus.cause != null) {
            errorEvent grpcStatus.cause.class, grpcStatus.cause.message, 1
          }
          attributes {
            "${SemanticAttributes.RPC_SERVICE.key()}" "Greeter"
            "${SemanticAttributes.NET_PEER_IP.key()}" "127.0.0.1"
            "${SemanticAttributes.NET_PEER_PORT.key()}" Long
          }
        }
      }
    }

    cleanup:
    channel?.shutdownNow()?.awaitTermination(10, TimeUnit.SECONDS)
    server?.shutdownNow()?.awaitTermination()

    where:
    name                          | grpcStatus
    "Runtime - cause"             | Status.UNKNOWN.withCause(new RuntimeException("some error"))
    "Status - cause"              | Status.PERMISSION_DENIED.withCause(new RuntimeException("some error"))
    "StatusRuntime - cause"       | Status.UNIMPLEMENTED.withCause(new RuntimeException("some error"))
    "Runtime - description"       | Status.UNKNOWN.withDescription("some description")
    "Status - description"        | Status.PERMISSION_DENIED.withDescription("some description")
    "StatusRuntime - description" | Status.UNIMPLEMENTED.withDescription("some description")
  }
}
