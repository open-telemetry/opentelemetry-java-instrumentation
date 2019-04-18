import akka.NotUsed
import akka.stream.javadsl.Source
import akka.stream.testkit.TestSubscriber.Probe
import akka.stream.testkit.javadsl.TestSink
import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.api.DDSpanTypes
import io.opentracing.tag.Tags
import play.inject.guice.GuiceApplicationBuilder
import spock.lang.Shared

import java.util.function.Function

import static com.lightbend.lagom.javadsl.testkit.ServiceTest.TestServer
import static com.lightbend.lagom.javadsl.testkit.ServiceTest.defaultSetup
import static com.lightbend.lagom.javadsl.testkit.ServiceTest.startServer

class LagomTest extends AgentTestRunner {

  @Shared
  private TestServer server

  def setupSpec() {
    server = startServer(defaultSetup()
      .withCluster(false)
      .withPersistence(false)
      .withCassandra(false)
      .withJdbc(false)
      .configureBuilder(
      new Function<GuiceApplicationBuilder, GuiceApplicationBuilder>() {
        @Override
        GuiceApplicationBuilder apply(GuiceApplicationBuilder builder) {
          return builder
            .bindings(new ServiceTestModule())
        }
      }))
  }

  def cleanupSpec() {
    server.stop()
  }

  def "normal request traces"() {
    setup:
    EchoService service = server.client(EchoService)

    Source<String, NotUsed> input =
      Source.from(Arrays.asList("msg1", "msg2", "msg3"))
        .concat(Source.maybe())
    Source<String, NotUsed> output = service.echo().invoke(input)
      .toCompletableFuture().get()
    Probe<String> probe = output.runWith(TestSink.probe(server.system()), server.materializer())
    probe.request(10)
    probe.expectNext("msg1")
    probe.expectNext("msg2")
    probe.expectNext("msg3")
    probe.cancel()

    expect:
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          serviceName "unnamed-java-app"
          operationName "akka-http.request"
          resourceName "GET ws://?/echo"
          spanType DDSpanTypes.HTTP_SERVER
          errored false
          tags {
            defaultTags()
            "$Tags.HTTP_STATUS.key" 101
            "$Tags.HTTP_URL.key" "ws://localhost:${server.port()}/echo"
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_SERVER
            "$Tags.COMPONENT.key" "akka-http-server"
          }
        }
        span(1) {
          childOf span(0)
          operationName 'EchoServiceImpl.tracedMethod'
        }
      }
    }
  }

  def "error traces"() {
    setup:
    EchoService service = server.client(EchoService)

    Source<String, NotUsed> input =
      Source.from(Arrays.asList("msg1", "msg2", "msg3"))
        .concat(Source.maybe())
    try {
      service.error().invoke(input).toCompletableFuture().get()
    } catch (Exception e) {
    }

    expect:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          serviceName "unnamed-java-app"
          operationName "akka-http.request"
          resourceName "GET ws://?/error"
          spanType DDSpanTypes.HTTP_SERVER
          errored true
          tags {
            defaultTags()
            "$Tags.HTTP_STATUS.key" 500
            "$Tags.HTTP_URL.key" "ws://localhost:${server.port()}/error"
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_SERVER
            "$Tags.COMPONENT.key" "akka-http-server"
            "$Tags.ERROR.key" true
          }
        }
      }
    }
  }
}
