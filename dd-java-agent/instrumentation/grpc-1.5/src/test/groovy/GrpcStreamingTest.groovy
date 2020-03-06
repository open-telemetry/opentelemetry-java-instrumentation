import datadog.opentracing.scopemanager.ContinuableScope
import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.api.DDSpanTypes
import datadog.trace.bootstrap.instrumentation.api.Tags
import example.GreeterGrpc
import example.Helloworld
import io.grpc.BindableService
import io.grpc.ManagedChannel
import io.grpc.Server
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import io.grpc.stub.StreamObserver

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
              if ((testTracer.scopeManager().active() as ContinuableScope).isAsyncPropagating()) {
                // The InProcessTransport calls the client response in process, so we have to disable async propagation.
                observer.onNext(value)
              } else {
                observer.onError(new IllegalStateException("not async propagating!"))
              }
            }
          }

          @Override
          void onError(Throwable t) {
            if ((testTracer.scopeManager().active() as ContinuableScope).isAsyncPropagating()) {
              // The InProcessTransport calls the client response in process, so we have to disable async propagation.
              error.set(t)
              observer.onError(t)
            } else {
              observer.onError(new IllegalStateException("not async propagating!"))
            }
          }

          @Override
          void onCompleted() {
            if ((testTracer.scopeManager().active() as ContinuableScope).isAsyncPropagating()) {
              // The InProcessTransport calls the client response in process, so we have to disable async propagation.
              observer.onCompleted()
            } else {
              observer.onError(new IllegalStateException("not async propagating!"))
            }
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
        if ((testTracer.scopeManager().active() as ContinuableScope).isAsyncPropagating()) {
          clientReceived << value.message
        } else {
          error.set(new IllegalStateException("not async propagating!"))
        }
      }

      @Override
      void onError(Throwable t) {
        if ((testTracer.scopeManager().active() as ContinuableScope).isAsyncPropagating()) {
          error.set(t)
        } else {
          error.set(new IllegalStateException("not async propagating!"))
        }
      }

      @Override
      void onCompleted() {
        if ((testTracer.scopeManager().active() as ContinuableScope).isAsyncPropagating()) {
          TEST_WRITER.waitForTraces(1)
        } else {
          error.set(new IllegalStateException("not async propagating!"))
        }
      }
    })

    clientRange.each {
      def message = Helloworld.Response.newBuilder().setMessage("call $it").build()
      observer.onNext(message)
    }
    observer.onCompleted()

    then:
    error.get() == null
    TEST_WRITER.waitForTraces(2)
    error.get() == null
    serverReceived == clientRange.collect { "call $it" }
    clientReceived == serverRange.collect { clientRange.collect { "call $it" } }.flatten().sort()

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
            "$Tags.COMPONENT" "grpc-server"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_SERVER
            "status.code" "OK"
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
              "$Tags.COMPONENT" "grpc-server"
              "$Tags.SPAN_KIND" Tags.SPAN_KIND_SERVER
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
            "$Tags.COMPONENT" "grpc-client"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            "status.code" "OK"
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
              "$Tags.COMPONENT" "grpc-client"
              "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
              "message.type" "example.Helloworld\$Response"
              defaultTags()
            }
          }
        }
      }
    }

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
