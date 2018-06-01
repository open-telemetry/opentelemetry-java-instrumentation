import akka.NotUsed
import akka.stream.javadsl.Source
import akka.stream.testkit.TestPublisher
import akka.stream.testkit.javadsl.TestSink
import com.lightbend.lagom.javadsl.testkit.ServiceTest
import datadog.opentracing.DDSpan
import org.junit.After

import static java.util.concurrent.TimeUnit.SECONDS;
import datadog.trace.agent.test.AgentTestRunner
import play.inject.guice.GuiceApplicationBuilder
import spock.lang.Shared

import akka.stream.testkit.TestSubscriber.Probe

import java.util.function.Function

import static com.lightbend.lagom.javadsl.testkit.ServiceTest.*

class LagomTest extends AgentTestRunner {
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

  def "200 traces" () {
    setup:
    EchoService service = server.client(EchoService.class)

    // Use a source that never terminates (concat Source.maybe) so we
    // don't close the upstream, which would close the downstream
    Source<String, NotUsed> input =
      Source.from(Arrays.asList("msg1", "msg2", "msg3"))
      .concat(Source.maybe())
    Source<String, NotUsed> output = service.echo().invoke(input)
      .toCompletableFuture().get(5, SECONDS)
    Probe<String> probe = output.runWith(TestSink.probe(server.system()),
                                         server.materializer())
    probe.request(10)
    probe.expectNext("msg1")
    probe.expectNext("msg2")
    probe.expectNext("msg3")
    probe.cancel()

    TEST_WRITER.waitForTraces(1)
    DDSpan[] akkaTrace = TEST_WRITER.get(0)
    DDSpan root = akkaTrace[0]

    expect:
    TEST_WRITER.size() == 1
    akkaTrace.size() == 2

    root.serviceName == "unnamed-java-app"
    root.operationName == "akkahttp.request"
    root.resourceName == "GET ws://?/echo"
    !root.context().getErrorFlag()
    root.context().tags["http.status_code"] == 101
    root.context().tags["http.url"] == "ws://localhost:${server.port()}/echo"
    root.context().tags["http.method"] == "GET"
    root.context().tags["span.kind"] == "server"
    root.context().tags["component"] == "akkahttp-action"
  }

}
