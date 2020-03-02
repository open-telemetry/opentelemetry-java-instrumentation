/*
 * Copyright 2020, OpenTelemetry Authors
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
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import io.grpc.stub.StreamObserver
import io.opentelemetry.auto.instrumentation.api.MoreTags
import io.opentelemetry.auto.instrumentation.api.SpanTypes
import io.opentelemetry.auto.instrumentation.api.Tags
import io.opentelemetry.auto.test.AgentTestRunner
import io.opentelemetry.auto.test.utils.PortUtils

import java.util.concurrent.TimeUnit

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
        responseObserver.onNext(reply)
        responseObserver.onCompleted()
      }
    }
    def port = PortUtils.randomOpenPort()
    Server server = ServerBuilder.forPort(port).addService(greeter).build().start()
    ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", port).usePlaintext(true).build()
    GreeterGrpc.GreeterBlockingStub client = GreeterGrpc.newBlockingStub(channel)

    when:
    def response = client.sayHello(Helloworld.Request.newBuilder().setName(name).build())

    then:
    response.message == "Hello $name"

    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          operationName "example.Greeter/SayHello"
          spanKind CLIENT
          parent()
          errored false
          event(0) {
            eventName "message"
            attributes {
              "message.type" "SENT"
              "message.id" 1
            }
          }
          tags {
            "$MoreTags.RESOURCE_NAME" "example.Greeter/SayHello"
            "$MoreTags.SPAN_TYPE" SpanTypes.RPC
            "$MoreTags.RPC_SERVICE" "Greeter"
            "$Tags.COMPONENT" "grpc-client"
            "net.peer.name" "localhost"
            "net.peer.port" port
            "status.code" "OK"
          }
        }
        span(1) {
          operationName "example.Greeter/SayHello"
          spanKind SERVER
          childOf span(0)
          errored false
          event(0) {
            eventName "message"
            attributes {
              "message.type" "RECEIVED"
              "message.id" 1
            }
          }
          tags {
            "$MoreTags.RESOURCE_NAME" "example.Greeter/SayHello"
            "$MoreTags.SPAN_TYPE" SpanTypes.RPC
            "$MoreTags.RPC_SERVICE" "Greeter"
            "$Tags.COMPONENT" "grpc-server"
            "net.peer.name" "localhost"
            "net.peer.port" Long
            "status.code" "OK"
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
    def error = status.asException()
    BindableService greeter = new GreeterGrpc.GreeterImplBase() {
      @Override
      void sayHello(
        final Helloworld.Request req, final StreamObserver<Helloworld.Response> responseObserver) {
        responseObserver.onError(error)
      }
    }
    def port = PortUtils.randomOpenPort()
    Server server = ServerBuilder.forPort(port).addService(greeter).build().start()
    ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", port).usePlaintext(true).build()
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
          tags {
            "$MoreTags.RESOURCE_NAME" "example.Greeter/SayHello"
            "$MoreTags.SPAN_TYPE" SpanTypes.RPC
            "$MoreTags.RPC_SERVICE" "Greeter"
            "$Tags.COMPONENT" "grpc-client"
            "status.code" "${status.code.name()}"
            "status.description" description
            "net.peer.name" "localhost"
            "net.peer.port" port
          }
        }
        span(1) {
          operationName "example.Greeter/SayHello"
          spanKind SERVER
          childOf span(0)
          errored true
          event(0) {
            eventName "message"
            attributes {
              "message.type" "RECEIVED"
              "message.id" 1
            }
          }
          tags {
            "$MoreTags.RESOURCE_NAME" "example.Greeter/SayHello"
            "$MoreTags.SPAN_TYPE" SpanTypes.RPC
            "$Tags.COMPONENT" "grpc-server"
            "$MoreTags.RPC_SERVICE" "Greeter"
            "status.code" "${status.code.name()}"
            "status.description" description
            "net.peer.name" "localhost"
            "net.peer.port" Long
            if (status.cause != null) {
              errorTags status.cause.class, status.cause.message
            }
          }
        }
      }
    }

    cleanup:
    channel?.shutdownNow()?.awaitTermination(10, TimeUnit.SECONDS)
    server?.shutdownNow()?.awaitTermination()

    where:
    name                          | status                                                                 | description
    "Runtime - cause"             | Status.UNKNOWN.withCause(new RuntimeException("some error"))           | null
    "Status - cause"              | Status.PERMISSION_DENIED.withCause(new RuntimeException("some error")) | null
    "StatusRuntime - cause"       | Status.UNIMPLEMENTED.withCause(new RuntimeException("some error"))     | null
    "Runtime - description"       | Status.UNKNOWN.withDescription("some description")                     | "some description"
    "Status - description"        | Status.PERMISSION_DENIED.withDescription("some description")           | "some description"
    "StatusRuntime - description" | Status.UNIMPLEMENTED.withDescription("some description")               | "some description"
  }

  def "test error thrown - #name"() {
    setup:
    def error = status.asRuntimeException()
    BindableService greeter = new GreeterGrpc.GreeterImplBase() {
      @Override
      void sayHello(
        final Helloworld.Request req, final StreamObserver<Helloworld.Response> responseObserver) {
        throw error
      }
    }
    Server server = InProcessServerBuilder.forName(getClass().name).addService(greeter).directExecutor().build().start()

    ManagedChannel channel = InProcessChannelBuilder.forName(getClass().name).build()
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
          tags {
            "$MoreTags.RESOURCE_NAME" "example.Greeter/SayHello"
            "$MoreTags.SPAN_TYPE" SpanTypes.RPC
            "$MoreTags.RPC_SERVICE" "Greeter"
            "$Tags.COMPONENT" "grpc-client"
            "status.code" "UNKNOWN"
            "net.peer.name" "(unknown)"
            "net.peer.port" 0
          }
        }
        span(1) {
          operationName "example.Greeter/SayHello"
          spanKind SERVER
          childOf span(0)
          errored true
          event(0) {
            eventName "message"
            attributes {
              "message.type" "RECEIVED"
              "message.id" 1
            }
          }
          tags {
            "$MoreTags.RESOURCE_NAME" "example.Greeter/SayHello"
            "$MoreTags.SPAN_TYPE" SpanTypes.RPC
            "$Tags.COMPONENT" "grpc-server"
            "$MoreTags.RPC_SERVICE" "Greeter"
            errorTags error.class, error.message
          }
        }
      }
    }

    cleanup:
    channel?.shutdownNow()?.awaitTermination(10, TimeUnit.SECONDS)
    server?.shutdownNow()?.awaitTermination()

    where:
    name                          | status
    "Runtime - cause"             | Status.UNKNOWN.withCause(new RuntimeException("some error"))
    "Status - cause"              | Status.PERMISSION_DENIED.withCause(new RuntimeException("some error"))
    "StatusRuntime - cause"       | Status.UNIMPLEMENTED.withCause(new RuntimeException("some error"))
    "Runtime - description"       | Status.UNKNOWN.withDescription("some description")
    "Status - description"        | Status.PERMISSION_DENIED.withDescription("some description")
    "StatusRuntime - description" | Status.UNIMPLEMENTED.withDescription("some description")
  }
}
