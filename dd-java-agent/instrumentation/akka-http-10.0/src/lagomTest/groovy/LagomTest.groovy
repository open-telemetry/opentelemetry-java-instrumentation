import akka.NotUsed
import akka.stream.javadsl.Source
import akka.stream.testkit.javadsl.TestSink
import datadog.trace.api.DDSpanTypes
import datadog.trace.api.DDTags
import io.opentracing.tag.Tags
import net.bytebuddy.utility.JavaModule

import datadog.trace.agent.test.AgentTestRunner
import play.inject.guice.GuiceApplicationBuilder
import spock.lang.Shared

import akka.stream.testkit.TestSubscriber.Probe

import java.util.function.Function

import static com.lightbend.lagom.javadsl.testkit.ServiceTest.*
import static datadog.trace.agent.test.ListWriterAssert.assertTraces

class LagomTest extends AgentTestRunner {
  static {
    System.setProperty("dd.integration.akka-http-server.enabled", "true")
  }

  @Shared
  private TestServer server

  @Override
  protected boolean onInstrumentationError(String typeName, ClassLoader classLoader, JavaModule module, boolean loaded, Throwable throwable) {
    if (throwable.getMessage().contains('Cannot resolve type description for akka.stream.impl.VirtualProcessor$WrappedSubscription$$SubscriptionState')) {
      // 'akka/stream/impl/VirtualProcessor$WrappedSubscription$PassThrough$.class' declares
      // itself an implementation of 'VirtualProcessor$WrappedSubscription$$SubscriptionState',
      // but this interface does not exist on the classpath.
      // The closest thing on the classpath is 'VirtualProcessor$WrappedSubscription$SubscriptionState' (only one $).

      // Looks like a compiler/packaging issue on akka's end. Or maybe this interface is dynamically generated.
      return false
    }
    return super.onInstrumentationError(typeName, classLoader, module, loaded, throwable)
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
      .toCompletableFuture().get()
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
          operationName "akka-http.request"
          resourceName  "GET ws://?/echo"
          errored false
          tags {
            defaultTags()
            "$Tags.HTTP_STATUS.key" 101
            "$Tags.HTTP_URL.key" "ws://localhost:${server.port()}/echo"
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_SERVER
            "$DDTags.SPAN_TYPE" DDSpanTypes.WEB_SERVLET
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

  def "error traces" () {
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
    assertTraces(TEST_WRITER, 1) {
      trace(0, 1) {
        span(0) {
          serviceName "unnamed-java-app"
          operationName "akka-http.request"
          resourceName "GET ws://?/error"
          errored true
          tags {
            defaultTags()
            "$Tags.HTTP_STATUS.key" 500
            "$Tags.HTTP_URL.key" "ws://localhost:${server.port()}/error"
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_SERVER
            "$DDTags.SPAN_TYPE" DDSpanTypes.WEB_SERVLET
            "$Tags.COMPONENT.key" "akka-http-server"
            "$Tags.ERROR.key" true
          }
        }
      }
    }
  }
}
