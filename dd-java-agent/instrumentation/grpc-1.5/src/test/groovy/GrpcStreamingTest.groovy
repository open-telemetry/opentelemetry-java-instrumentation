import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.api.DDSpanTypes
import example.GreeterGrpc
import example.Helloworld
import io.grpc.BindableService
import io.grpc.ManagedChannel
import io.grpc.Server
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import io.grpc.stub.StreamObserver
import io.opentracing.tag.Tags

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class GrpcStreamingTest extends AgentTestRunner {

  def "test conversation #name"() {
    setup:
    def msgCount = serverMessageCount
    def serverReceived = new CopyOnWriteArrayList<>()
    def clientReceived = new CopyOnWriteArrayList<>()
    def error = new AtomicReference()

    BindableService greeter = new GreeterGrpc.GreeterImplBase() {
      @Override
      StreamObserver<Helloworld.Response> conversation(StreamObserver<Helloworld.Response> observer) {
        return new StreamObserver<Helloworld.Response>() {
          @Override
          void onNext(Helloworld.Response value) {
            serverReceived << value.message

            (1..msgCount).each {
              observer.onNext(value)
            }
          }

          @Override
          void onError(Throwable t) {
            error.set(t)
            observer.onError(t)
          }

          @Override
          void onCompleted() {
            observer.onCompleted()
          }
        }
      }
    }
    Server server = InProcessServerBuilder.forName(getClass().name).addService(greeter).directExecutor().build().start()

    ManagedChannel channel = InProcessChannelBuilder.forName(getClass().name).build()
    GreeterGrpc.GreeterStub client = GreeterGrpc.newStub(channel).withWaitForReady()

    when:
    def observer = client.conversation(new StreamObserver<Helloworld.Response>() {
      @Override
      void onNext(Helloworld.Response value) {
        clientReceived << value.message
      }

      @Override
      void onError(Throwable t) {
        error.set(t)
      }

      @Override
      void onCompleted() {
        TEST_WRITER.waitForTraces(1)
      }
    })

    clientRange.each {
      def message = Helloworld.Response.newBuilder().setMessage("call $it").build()
      observer.onNext(message)
    }
    observer.onCompleted()

    then:
    error.get() == null

    assertTraces(2) {
      trace(0, clientMessageCount + 1) {
        span(0) {
          serviceName "unnamed-java-app"
          operationName "grpc.server"
          resourceName "example.Greeter/Conversation"
          spanType DDSpanTypes.RPC
          childOf trace(1).get(0)
          errored false
          tags {
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_SERVER
            "$Tags.COMPONENT.key" "grpc-server"
            defaultTags(true)
          }
        }
        clientRange.each {
          span(it) {
            serviceName "unnamed-java-app"
            operationName "grpc.message"
            resourceName "grpc.message"
            spanType DDSpanTypes.RPC
            childOf span(0)
            errored false
            tags {
              "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_SERVER
              "$Tags.COMPONENT.key" "grpc-server"
              "message.type" "example.Helloworld\$Response"
              defaultTags()
            }
          }
        }
      }
      trace(1, (clientMessageCount * serverMessageCount) + 1) {
        span(0) {
          serviceName "unnamed-java-app"
          operationName "grpc.client"
          resourceName "example.Greeter/Conversation"
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
        (1..(clientMessageCount * serverMessageCount)).each {
          span(it) {
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
    }

    serverReceived == clientRange.collect { "call $it" }
    clientReceived == serverRange.collect { clientRange.collect { "call $it" } }.flatten().sort()

    cleanup:
    channel?.shutdownNow()?.awaitTermination(10, TimeUnit.SECONDS)
    server?.shutdownNow()?.awaitTermination()

    where:
    name | clientMessageCount | serverMessageCount
    "A"  | 1                  | 1
    "B"  | 2                  | 1
    "C"  | 1                  | 2
    "D"  | 2                  | 2
    "E"  | 3                  | 3

    clientRange = 1..clientMessageCount
    serverRange = 1..serverMessageCount
  }
}
