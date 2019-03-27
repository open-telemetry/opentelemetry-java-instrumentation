import com.fasterxml.jackson.databind.JsonNode
import datadog.opentracing.DDSpan
import datadog.opentracing.DDSpanContext
import datadog.opentracing.DDTracer
import datadog.opentracing.PendingTrace
import datadog.trace.api.sampling.PrioritySampling
import datadog.trace.common.writer.DDApi
import datadog.trace.common.writer.ListWriter
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.startupcheck.MinimumDurationRunningStartupCheckStrategy
import spock.lang.Requires
import spock.lang.Shared
import spock.lang.Specification

import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class DDApiIntegrationTest {
  // Do not run tests locally on Java7 since testcontainers are not compatible with Java7
  // It is fine to run on CI because CI provides rabbitmq externally, not through testcontainers
  @Requires({ "true" == System.getenv("CI") || jvm.java8Compatible })
  static class DDApiIntegrationV4Test extends Specification {
    static final WRITER = new ListWriter()
    static final TRACER = new DDTracer(WRITER)
    static final CONTEXT = new DDSpanContext(
      "1",
      "1",
      "0",
      "fakeService",
      "fakeOperation",
      "fakeResource",
      PrioritySampling.UNSET,
      null,
      Collections.emptyMap(),
      false,
      "fakeType",
      Collections.emptyMap(),
      new PendingTrace(TRACER, "1", [:]),
      TRACER)

    // Looks like okHttp needs to resolve this, even for connection over socket
    static final SOMEHOST = "datadoghq.com"
    static final SOMEPORT = 123

    /*
    Note: type here has to stay undefined, otherwise tests will fail in CI in Java 7 because
    'testcontainers' are built for Java 8 and Java 7 cannot load this class.
   */
    @Shared
    def agentContainer
    @Shared
    def agentContainerHost = "localhost"
    @Shared
    def agentContainerPort = 8126
    @Shared
    Process process
    @Shared
    File socketPath

    def api
    def unixDomainSocketApi

    def endpoint = new AtomicReference<String>(null)
    def agentResponse = new AtomicReference<String>(null)

    DDApi.ResponseListener responseListener = { String receivedEndpoint, JsonNode responseJson ->
      endpoint.set(receivedEndpoint)
      agentResponse.set(responseJson.toString())
    }

    def setupSpec() {

      /*
        CI will provide us with rabbitmq container running along side our build.
        When building locally, however, we need to take matters into our own hands
        and we use 'testcontainers' for this.
       */
      if ("true" != System.getenv("CI")) {
        agentContainer = new GenericContainer("datadog/docker-dd-agent:latest")
          .withEnv(["DD_APM_ENABLED": "true",
                    "DD_BIND_HOST"  : "0.0.0.0",
                    "DD_API_KEY"    : "invalid_key_but_this_is_fine",
                    "DD_LOGS_STDOUT": "yes"])
          .withExposedPorts(datadog.trace.api.Config.DEFAULT_TRACE_AGENT_PORT)
          .withStartupTimeout(Duration.ofSeconds(120))
        // Apparently we need to sleep for a bit so agent's response `{"service:,env:":1}` in rate_by_service.
        // This is clearly a race-condition and maybe we should av oid verifying complete response
          .withStartupCheckStrategy(new MinimumDurationRunningStartupCheckStrategy(Duration.ofSeconds(10)))
        //        .withLogConsumer { output ->
        //        print output.utf8String
        //      }
        agentContainer.start()
        agentContainerHost = agentContainer.containerIpAddress
        agentContainerPort = agentContainer.getMappedPort(datadog.trace.api.Config.DEFAULT_TRACE_AGENT_PORT)
      }

      File tmpDir = File.createTempDir()
      tmpDir.deleteOnExit()
      socketPath = new File(tmpDir, "socket")
      process = Runtime.getRuntime().exec("socat UNIX-LISTEN:${socketPath},reuseaddr,fork TCP-CONNECT:${agentContainerHost}:${agentContainerPort}")
    }

    def cleanupSpec() {
      if (agentContainer) {
        agentContainer.stop()
      }
      process.destroy()
    }

    def setup() {
      api = new DDApi(agentContainerHost, agentContainerPort, v4(), null)
      api.addResponseListener(responseListener)

      unixDomainSocketApi = new DDApi(SOMEHOST, SOMEPORT, v4(), socketPath.toString())
      unixDomainSocketApi.addResponseListener(responseListener)
    }

    boolean v4() {
      return true
    }

    def "Sending traces succeeds (test #test)"() {
      expect:
      api.sendTraces(traces)
      if (v4()) {
        assert endpoint.get() == "http://${agentContainerHost}:${agentContainerPort}/v0.4/traces"
        assert agentResponse.get() == '{"rate_by_service":{"service:,env:":1}}'
      }

      where:
      traces                                                                              | test
      []                                                                                  | 1
      [[], []]                                                                            | 2
      [[new DDSpan(1, CONTEXT)]]                                                          | 3
      [[new DDSpan(TimeUnit.MILLISECONDS.toMicros(System.currentTimeMillis()), CONTEXT)]] | 4
      (1..15).collect { [] }                                                              | 5
      (1..16).collect { [] }                                                              | 6
      // Larger traces take more than 1 second to send to the agent and get a timeout exception:
//      (1..((1 << 16) - 1)).collect { [] }                                                 | 7
//      (1..(1 << 16)).collect { [] }                                                       | 8
    }

    def "Sending traces to unix domain socket succeeds (test #test)"() {
      expect:
      unixDomainSocketApi.sendTraces(traces)
      if (v4()) {
        assert endpoint.get() == "http://${SOMEHOST}:${SOMEPORT}/v0.4/traces"
        assert agentResponse.get() == '{"rate_by_service":{"service:,env:":1}}'
      }

      where:
      traces                                                                              | test
      []                                                                                  | 1
      [[], []]                                                                            | 2
      [[new DDSpan(1, CONTEXT)]]                                                          | 3
      [[new DDSpan(TimeUnit.MILLISECONDS.toMicros(System.currentTimeMillis()), CONTEXT)]] | 4
    }
  }

  @Requires({ "true" == System.getenv("CI") || jvm.java8Compatible })
  static class DDApiIntegrationV3Test extends DDApiIntegrationV4Test {
    boolean v4() {
      return false
    }

    def cleanup() {
      assert endpoint.get() == null
      assert agentResponse.get() == null
    }
  }
}
