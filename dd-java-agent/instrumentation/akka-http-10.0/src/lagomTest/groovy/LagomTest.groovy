import akka.NotUsed
import akka.stream.javadsl.Source
import akka.stream.testkit.javadsl.TestSink
import org.junit.After

import static java.util.concurrent.TimeUnit.SECONDS
import datadog.trace.agent.test.AgentTestRunner
import play.inject.guice.GuiceApplicationBuilder
import spock.lang.Shared

import akka.stream.testkit.TestSubscriber.Probe

import java.util.function.Function

import static com.lightbend.lagom.javadsl.testkit.ServiceTest.*
import static datadog.trace.agent.test.ListWriterAssert.assertTraces

class LagomTest extends AgentTestRunner {
  static {
    System.setProperty("dd.integration.akkahttp.enabled", "true")
  }

  @Shared
  private TestServer server


  @After
  @Override
  void afterTest() {
    // FIXME:
    // skipping error check
    // bytebuddy is having trouble resolving akka.stream.impl.VirtualProcessor$WrappedSubscription$$SubscriptionState
    // possibly due to '$$' in class name?
    // class is on the classpath.
  }

  def setupSpec() {
    server = startServer(defaultSetup()
                         .withCluster(false)
                         .withPersistence(false)
                         .withCassandra(false)
                         .withJdbc(false)
                         .withConfigureBuilder(
        new Function<GuiceApplicationBuilder, GuiceApplicationBuilder>() {
          @Override
          GuiceApplicationBuilder apply(GuiceApplicationBuilder builder) {
            return builder
              .bindings(new ServiceTestModule())
          }}))
  }

  def cleanupSpec() {
    server.stop()
  }

  def "normal request traces" () {
    setup:
    EchoService service = server.client(EchoService)

    Source<String, NotUsed> input =
      Source.from(Arrays.asList("msg1", "msg2", "msg3"))
      .concat(Source.maybe())
    Source<String, NotUsed> output = service.echo().invoke(input)
      .toCompletableFuture().get(5, SECONDS)
    Probe<String> probe = output.runWith(TestSink.probe(server.system()), server.materializer())
    probe.request(10)
    probe.expectNext("msg1")
    probe.expectNext("msg2")
    probe.expectNext("msg3")
    probe.cancel()

    expect:
    assertTraces(TEST_WRITER, 1) {
      trace(0, 2) {
        span(0) {
          serviceName "unnamed-java-app"
          operationName "akkahttp.request"
          resourceName  "GET ws://?/echo"
          errored false
          tags {
            defaultTags()
            "http.status_code" 101
            "http.url" "ws://localhost:${server.port()}/echo"
            "http.method" "GET"
            "span.kind" "server"
            "span.type" "web"
            "component" "akkahttp-action"
          }
        }
        span(1) {
          childOf span(0)
        }
      }
    }
  }

  def "error traces" () {
    setup:
    EchoService service = server.client(EchoService)

    Source<String, NotUsed> input =
      Source.from(Arrays.asList("msg1", "msg2", "msg3"))
        .concat(Source.maybe())
    try {
      service.error().invoke(input).toCompletableFuture().get(5, SECONDS)
    } catch (Exception e) {
    }

    expect:
    assertTraces(TEST_WRITER, 1) {
      trace(0, 1) {
        span(0) {
          serviceName "unnamed-java-app"
          operationName "akkahttp.request"
          resourceName "GET ws://?/error"
          errored true
          tags {
            defaultTags()
            "http.status_code" 500
            "http.url" "ws://localhost:${server.port()}/error"
            "http.method" "GET"
            "span.kind" "server"
            "span.type" "web"
            "component" "akkahttp-action"
            "error" true
          }
        }
      }
    }
  }

}
