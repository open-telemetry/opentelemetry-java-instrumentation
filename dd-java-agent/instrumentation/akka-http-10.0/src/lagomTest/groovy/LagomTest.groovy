import akka.NotUsed
import akka.stream.javadsl.Source
import akka.stream.testkit.TestSubscriber.Probe
import akka.stream.testkit.javadsl.TestSink
import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.api.DDSpanTypes
import datadog.trace.bootstrap.instrumentation.api.Tags
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
          resourceName "GET /echo"
          spanType DDSpanTypes.HTTP_SERVER
          errored false
          tags {
            "$Tags.COMPONENT" "akka-http-server"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_SERVER
            "$Tags.HTTP_URL" "ws://localhost:${server.port()}/echo"
            "$Tags.HTTP_METHOD" "GET"
            "$Tags.HTTP_STATUS" 101
            defaultTags()
          }
        }
        span(1) {
          childOf span(0)
          operationName 'trace.annotation'
          resourceName 'EchoServiceImpl.tracedMethod'
          tags {
            "$Tags.COMPONENT" "trace"
            defaultTags()
          }
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
          resourceName "GET /error"
          spanType DDSpanTypes.HTTP_SERVER
          errored true
          tags {
            "$Tags.COMPONENT" "akka-http-server"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_SERVER
            "$Tags.HTTP_URL" "ws://localhost:${server.port()}/error"
            "$Tags.HTTP_METHOD" "GET"
            "$Tags.HTTP_STATUS" 500
            defaultTags()
          }
        }
      }
    }
  }
}
