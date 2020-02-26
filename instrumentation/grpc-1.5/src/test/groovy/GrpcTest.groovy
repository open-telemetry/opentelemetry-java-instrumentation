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
import io.grpc.Server
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import io.grpc.stub.StreamObserver
import io.opentelemetry.auto.instrumentation.api.MoreTags
import io.opentelemetry.auto.instrumentation.api.SpanTypes
import io.opentelemetry.auto.instrumentation.api.Tags
import io.opentelemetry.auto.test.AgentTestRunner
import io.opentelemetry.sdk.trace.SpanData

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
    Server server = InProcessServerBuilder.forName(getClass().name).addService(greeter).directExecutor().build().start()

    ManagedChannel channel = InProcessChannelBuilder.forName(getClass().name).build()
    GreeterGrpc.GreeterBlockingStub client = GreeterGrpc.newBlockingStub(channel)

    when:
    def response = client.sayHello(Helloworld.Request.newBuilder().setName(name).build())

    then:
    response.message == "Hello $name"

    assertTraces(1) {
      trace(0, 4) {
        sortSpans {
          // sort for consistent ordering
          List<SpanData> serverMessages = new ArrayList<>()
          for (SpanData span : spans) {
            if (span.name == "grpc.message" && span.attributes[Tags.COMPONENT].stringValue == "grpc-server") {
              serverMessages.add(span)
            }
            if (span.name == "grpc.server" && span.attributes[Tags.COMPONENT].stringValue == "grpc-server") {
              serverMessages.add(0, span)
            }
          }
          // move the server messages to the end
          spans.removeAll(serverMessages)
          spans.addAll(serverMessages)
        }
        span(0) {
          operationName "grpc.client"
          spanKind CLIENT
          parent()
          errored false
          tags {
            "$MoreTags.RESOURCE_NAME" "example.Greeter/SayHello"
            "$MoreTags.SPAN_TYPE" SpanTypes.RPC
            "$Tags.COMPONENT" "grpc-client"
            "status.code" "OK"
          }
        }
        span(1) {
          operationName "grpc.message"
          spanKind CLIENT
          childOf span(0)
          errored false
          tags {
            "$MoreTags.SPAN_TYPE" SpanTypes.RPC
            "$Tags.COMPONENT" "grpc-client"
            "message.type" "example.Helloworld\$Response"
          }
        }
        span(2) {
          operationName "grpc.server"
          spanKind SERVER
          childOf span(0)
          errored false
          tags {
            "$MoreTags.RESOURCE_NAME" "example.Greeter/SayHello"
            "$MoreTags.SPAN_TYPE" SpanTypes.RPC
            "$Tags.COMPONENT" "grpc-server"
            "status.code" "OK"
          }
        }
        span(3) {
          operationName "grpc.message"
          spanKind SERVER
          childOf span(2)
          errored false
          tags {
            "$MoreTags.SPAN_TYPE" SpanTypes.RPC
            "$Tags.COMPONENT" "grpc-server"
            "message.type" "example.Helloworld\$Request"
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
    Server server = InProcessServerBuilder.forName(getClass().name).addService(greeter).directExecutor().build().start()

    ManagedChannel channel = InProcessChannelBuilder.forName(getClass().name).build()
    GreeterGrpc.GreeterBlockingStub client = GreeterGrpc.newBlockingStub(channel)

    when:
    client.sayHello(Helloworld.Request.newBuilder().setName(name).build())

    then:
    thrown StatusRuntimeException

    assertTraces(1) {
      trace(0, 3) {
        span(0) {
          operationName "grpc.client"
          spanKind CLIENT
          parent()
          errored true
          tags {
            "$MoreTags.RESOURCE_NAME" "example.Greeter/SayHello"
            "$MoreTags.SPAN_TYPE" SpanTypes.RPC
            "$Tags.COMPONENT" "grpc-client"
            "status.code" "${status.code.name()}"
            "status.description" description
          }
        }
        span(1) {
          operationName "grpc.server"
          spanKind SERVER
          childOf span(0)
          errored true
          tags {
            "$MoreTags.RESOURCE_NAME" "example.Greeter/SayHello"
            "$MoreTags.SPAN_TYPE" SpanTypes.RPC
            "$Tags.COMPONENT" "grpc-server"
            "status.code" "${status.code.name()}"
            "status.description" description
            if (status.cause != null) {
              errorTags status.cause.class, status.cause.message
            }
          }
        }
        span(2) {
          operationName "grpc.message"
          spanKind SERVER
          childOf span(1)
          errored false
          tags {
            "$MoreTags.SPAN_TYPE" SpanTypes.RPC
            "$Tags.COMPONENT" "grpc-server"
            "message.type" "example.Helloworld\$Request"
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
      trace(0, 3) {
        span(0) {
          operationName "grpc.client"
          spanKind CLIENT
          parent()
          errored true
          tags {
            "$MoreTags.RESOURCE_NAME" "example.Greeter/SayHello"
            "$MoreTags.SPAN_TYPE" SpanTypes.RPC
            "$Tags.COMPONENT" "grpc-client"
            "status.code" "UNKNOWN"
          }
        }
        span(1) {
          operationName "grpc.server"
          spanKind SERVER
          childOf span(0)
          errored true
          tags {
            "$MoreTags.RESOURCE_NAME" "example.Greeter/SayHello"
            "$MoreTags.SPAN_TYPE" SpanTypes.RPC
            "$Tags.COMPONENT" "grpc-server"
            errorTags error.class, error.message
          }
        }
        span(2) {
          operationName "grpc.message"
          spanKind SERVER
          childOf span(1)
          errored false
          tags {
            "$MoreTags.SPAN_TYPE" SpanTypes.RPC
            "$Tags.COMPONENT" "grpc-server"
            "message.type" "example.Helloworld\$Request"
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
