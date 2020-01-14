import example.GreeterGrpc
import example.Helloworld
import io.grpc.BindableService
import io.grpc.ManagedChannel
import io.grpc.Server
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import io.grpc.stub.StreamObserver
import io.opentelemetry.auto.api.MoreTags
import io.opentelemetry.auto.api.SpanTypes
import io.opentelemetry.auto.instrumentation.api.Tags
import io.opentelemetry.auto.test.AgentTestRunner
import io.opentelemetry.sdk.trace.SpanData

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
      }
    })

    clientRange.each {
      def message = Helloworld.Response.newBuilder().setMessage("call $it").build()
      observer.onNext(message)
    }
    observer.onCompleted()

    then:
    error.get() == null

    assertTraces(1) {
      trace(0, clientMessageCount * serverMessageCount + 1 + clientMessageCount + 1) {
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
            "$MoreTags.RESOURCE_NAME" "example.Greeter/Conversation"
            "$MoreTags.SPAN_TYPE" SpanTypes.RPC
            "$Tags.COMPONENT" "grpc-client"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            "status.code" "OK"
          }
        }
        (1..(clientMessageCount * serverMessageCount)).each {
          println it
          span(it) {
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
        }
        span(clientMessageCount * serverMessageCount + 1) {
          operationName "grpc.server"
          childOf span(0)
          errored false
          tags {
            "$MoreTags.RESOURCE_NAME" "example.Greeter/Conversation"
            "$MoreTags.SPAN_TYPE" SpanTypes.RPC
            "$Tags.COMPONENT" "grpc-server"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_SERVER
            "status.code" "OK"
          }
        }
        clientRange.each {
          span(clientMessageCount * serverMessageCount + 1 + it) {
            operationName "grpc.message"
            childOf span(clientMessageCount * serverMessageCount + 1)
            errored false
            tags {
              "$MoreTags.SPAN_TYPE" SpanTypes.RPC
              "$Tags.COMPONENT" "grpc-server"
              "$Tags.SPAN_KIND" Tags.SPAN_KIND_SERVER
              "message.type" "example.Helloworld\$Response"
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
