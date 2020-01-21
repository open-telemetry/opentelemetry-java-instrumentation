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
import io.opentelemetry.auto.api.MoreTags
import io.opentelemetry.auto.api.SpanTypes
import io.opentelemetry.auto.instrumentation.api.Tags
import io.opentelemetry.auto.test.AgentTestRunner
import io.opentelemetry.sdk.trace.SpanData

import java.util.concurrent.TimeUnit

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
          parent()
          errored false
          tags {
            "$MoreTags.RESOURCE_NAME" "example.Greeter/SayHello"
            "$MoreTags.SPAN_TYPE" SpanTypes.RPC
            "$Tags.COMPONENT" "grpc-client"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            "status.code" "OK"
          }
        }
        span(1) {
          operationName "grpc.message"
          childOf span(0)
          errored false
          tags {
            "$MoreTags.SPAN_TYPE" SpanTypes.RPC
            "$Tags.COMPONENT" "grpc-client"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            "message.type" "example.Helloworld\$Response"
          }
        }
        span(2) {
          operationName "grpc.server"
          childOf span(0)
          errored false
          tags {
            "$MoreTags.RESOURCE_NAME" "example.Greeter/SayHello"
            "$MoreTags.SPAN_TYPE" SpanTypes.RPC
            "$Tags.COMPONENT" "grpc-server"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_SERVER
            "status.code" "OK"
          }
        }
        span(3) {
          operationName "grpc.message"
          childOf span(2)
          errored false
          tags {
            "$MoreTags.SPAN_TYPE" SpanTypes.RPC
            "$Tags.COMPONENT" "grpc-server"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_SERVER
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
          parent()
          errored true
          tags {
            "$MoreTags.RESOURCE_NAME" "example.Greeter/SayHello"
            "$MoreTags.SPAN_TYPE" SpanTypes.RPC
            "$Tags.COMPONENT" "grpc-client"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            "status.code" "${status.code.name()}"
            "status.description" description
          }
        }
        span(1) {
          operationName "grpc.server"
          childOf span(0)
          errored true
          tags {
            "$MoreTags.RESOURCE_NAME" "example.Greeter/SayHello"
            "$MoreTags.SPAN_TYPE" SpanTypes.RPC
            "$Tags.COMPONENT" "grpc-server"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_SERVER
            "status.code" "${status.code.name()}"
            "status.description" description
            if (status.cause != null) {
              errorTags status.cause.class, status.cause.message
            }
          }
        }
        span(2) {
          operationName "grpc.message"
          childOf span(1)
          errored false
          tags {
            "$MoreTags.SPAN_TYPE" SpanTypes.RPC
            "$Tags.COMPONENT" "grpc-server"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_SERVER
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
          parent()
          errored true
          tags {
            "$MoreTags.RESOURCE_NAME" "example.Greeter/SayHello"
            "$MoreTags.SPAN_TYPE" SpanTypes.RPC
            "$Tags.COMPONENT" "grpc-client"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            "status.code" "UNKNOWN"
          }
        }
        span(1) {
          operationName "grpc.server"
          childOf span(0)
          errored true
          tags {
            "$MoreTags.RESOURCE_NAME" "example.Greeter/SayHello"
            "$MoreTags.SPAN_TYPE" SpanTypes.RPC
            "$Tags.COMPONENT" "grpc-server"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_SERVER
            errorTags error.class, error.message
          }
        }
        span(2) {
          operationName "grpc.message"
          childOf span(1)
          errored false
          tags {
            "$MoreTags.SPAN_TYPE" SpanTypes.RPC
            "$Tags.COMPONENT" "grpc-server"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_SERVER
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
