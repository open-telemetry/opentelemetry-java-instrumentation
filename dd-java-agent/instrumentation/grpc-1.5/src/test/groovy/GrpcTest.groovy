import datadog.common.exec.CommonTaskExecutor
import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.api.DDSpanTypes
import datadog.trace.bootstrap.instrumentation.api.Tags
import datadog.trace.instrumentation.grpc.server.GrpcExtractAdapter
import example.GreeterGrpc
import example.Helloworld
import io.grpc.BindableService
import io.grpc.ManagedChannel
import io.grpc.Metadata
import io.grpc.Server
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import io.grpc.stub.StreamObserver

import java.util.concurrent.TimeUnit

import static datadog.trace.agent.test.utils.TraceUtils.basicSpan
import static datadog.trace.agent.test.utils.TraceUtils.runUnderTrace

class GrpcTest extends AgentTestRunner {

  def "test request-response"() {
    setup:
    BindableService greeter = new GreeterGrpc.GreeterImplBase() {
      @Override
      void sayHello(
        final Helloworld.Request req, final StreamObserver<Helloworld.Response> responseObserver) {
        final Helloworld.Response reply = Helloworld.Response.newBuilder().setMessage("Hello $req.name").build()
        CommonTaskExecutor.INSTANCE.execute {
          if (testTracer.activeSpan() == null) {
            responseObserver.onError(new IllegalStateException("no active span"))
          } else {
            responseObserver.onNext(reply)
            responseObserver.onCompleted()
          }
        }
      }
    }
    Server server = InProcessServerBuilder.forName(getClass().name).addService(greeter).directExecutor().build().start()

    ManagedChannel channel = InProcessChannelBuilder.forName(getClass().name).build()
    GreeterGrpc.GreeterBlockingStub client = GreeterGrpc.newBlockingStub(channel)

    when:
    def response = runUnderTrace("parent") {
      def resp = client.sayHello(Helloworld.Request.newBuilder().setName(name).build())
      TEST_WRITER.waitForTraces(1) // Wait for the server span to be reported.
      return resp
    }

    then:
    response.message == "Hello $name"
    assertTraces(2) {
      trace(0, 2) {
        span(0) {
          serviceName "unnamed-java-app"
          operationName "grpc.server"
          resourceName "example.Greeter/SayHello"
          spanType DDSpanTypes.RPC
          childOf trace(1).get(1)
          errored false
          tags {
            "$Tags.COMPONENT" "grpc-server"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_SERVER
            "status.code" "OK"
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
            "$Tags.COMPONENT" "grpc-server"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_SERVER
            "message.type" "example.Helloworld\$Request"
            defaultTags()
          }
        }
      }
      trace(1, 3) {
        basicSpan(it, 0, "parent")
        span(1) {
          serviceName "unnamed-java-app"
          operationName "grpc.client"
          resourceName "example.Greeter/SayHello"
          spanType DDSpanTypes.RPC
          childOf span(0)
          errored false
          tags {
            "$Tags.COMPONENT" "grpc-client"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            "status.code" "OK"
            defaultTags()
          }
        }
        span(2) {
          serviceName "unnamed-java-app"
          operationName "grpc.message"
          resourceName "grpc.message"
          spanType DDSpanTypes.RPC
          childOf span(1)
          errored false
          tags {
            "$Tags.COMPONENT" "grpc-client"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
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
          errored true
          tags {
            "$Tags.COMPONENT" "grpc-server"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_SERVER
            "status.code" "${status.code.name()}"
            "status.description" description
            if (status.cause != null) {
              errorTags status.cause.class, status.cause.message
            }
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
            "$Tags.COMPONENT" "grpc-server"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_SERVER
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
            "$Tags.COMPONENT" "grpc-client"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            "status.code" "${status.code.name()}"
            "status.description" description
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
            "$Tags.COMPONENT" "grpc-server"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_SERVER
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
            "$Tags.COMPONENT" "grpc-server"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_SERVER
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
            "$Tags.COMPONENT" "grpc-client"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            "status.code" "UNKNOWN"
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

  def "skip binary headers"() {
    setup:
    def meta = new Metadata()
    meta.put(Metadata.Key.<String> of("test", Metadata.ASCII_STRING_MARSHALLER), "val")
    meta.put(Metadata.Key.<byte[]> of("test-bin", Metadata.BINARY_BYTE_MARSHALLER), "bin-val".bytes)

    when:
    def keys = GrpcExtractAdapter.GETTER.keys(meta)

    then:
    keys == ["test"]
  }
}
