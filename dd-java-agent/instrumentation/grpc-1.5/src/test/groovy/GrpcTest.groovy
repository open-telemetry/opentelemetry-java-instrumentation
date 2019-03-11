import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.api.DDSpanTypes
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
import io.opentracing.tag.Tags

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
    assertTraces(2) {
      trace(0, 2) {
        span(0) {
          serviceName "unnamed-java-app"
          operationName "grpc.server"
          resourceName "example.Greeter/SayHello"
          spanType DDSpanTypes.RPC
          childOf trace(1).get(0)
          errored false
          tags {
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_SERVER
            "$Tags.COMPONENT.key" "grpc-server"
            defaultTags(true)
          }
        }
        span(1) {
          serviceName "unnamed-java-app"
          operationName "grpc.message"
          resourceName "grpc.message"
          spanType DDSpanTypes.RPC
          childOf span(0)
          errored false
          tags {
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_SERVER
            "$Tags.COMPONENT.key" "grpc-server"
            "message.type" "example.Helloworld\$Request"
            defaultTags()
          }
        }
      }
      trace(1, 2) {
        span(0) {
          serviceName "unnamed-java-app"
          operationName "grpc.client"
          resourceName "example.Greeter/SayHello"
          spanType DDSpanTypes.RPC
          parent()
          errored false
          tags {
            "status.code" "OK"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "$Tags.COMPONENT.key" "grpc-client"
            defaultTags()
          }
        }
        span(1) {
          serviceName "unnamed-java-app"
          operationName "grpc.message"
          resourceName "grpc.message"
          spanType DDSpanTypes.RPC
          childOf span(0)
          errored false
          tags {
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "$Tags.COMPONENT.key" "grpc-client"
            "message.type" "example.Helloworld\$Response"
            defaultTags()
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

    assertTraces(2) {
      trace(0, 2) {
        span(0) {
          serviceName "unnamed-java-app"
          operationName "grpc.server"
          resourceName "example.Greeter/SayHello"
          spanType DDSpanTypes.RPC
          childOf trace(1).get(0)
          errored false
          tags {
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_SERVER
            "$Tags.COMPONENT.key" "grpc-server"
            defaultTags(true)
          }
        }
        span(1) {
          serviceName "unnamed-java-app"
          operationName "grpc.message"
          resourceName "grpc.message"
          spanType DDSpanTypes.RPC
          childOf span(0)
          errored false
          tags {
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_SERVER
            "$Tags.COMPONENT.key" "grpc-server"
            "message.type" "example.Helloworld\$Request"
            defaultTags()
          }
        }
      }
      trace(1, 1) {
        span(0) {
          serviceName "unnamed-java-app"
          operationName "grpc.client"
          resourceName "example.Greeter/SayHello"
          spanType DDSpanTypes.RPC
          parent()
          errored true
          tags {
            "status.code" "${status.code.name()}"
            "status.description" description
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "$Tags.COMPONENT.key" "grpc-client"
            tag "error", true
            defaultTags()
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

    assertTraces(2) {
      trace(0, 2) {
        span(0) {
          serviceName "unnamed-java-app"
          operationName "grpc.server"
          resourceName "example.Greeter/SayHello"
          spanType DDSpanTypes.RPC
          childOf trace(1).get(0)
          errored true
          tags {
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_SERVER
            "$Tags.COMPONENT.key" "grpc-server"
            errorTags error.class, error.message
            defaultTags(true)
          }
        }
        span(1) {
          serviceName "unnamed-java-app"
          operationName "grpc.message"
          resourceName "grpc.message"
          spanType DDSpanTypes.RPC
          childOf span(0)
          errored false
          tags {
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_SERVER
            "$Tags.COMPONENT.key" "grpc-server"
            "message.type" "example.Helloworld\$Request"
            defaultTags()
          }
        }
      }
      trace(1, 1) {
        span(0) {
          serviceName "unnamed-java-app"
          operationName "grpc.client"
          resourceName "example.Greeter/SayHello"
          spanType DDSpanTypes.RPC
          parent()
          errored true
          tags {
            "status.code" "UNKNOWN"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "$Tags.COMPONENT.key" "grpc-client"
            tag "error", true
            defaultTags()
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
