import com.fasterxml.jackson.databind.JsonNode
import datadog.opentracing.DDSpan
import datadog.opentracing.DDSpanContext
import datadog.opentracing.DDTracer
import datadog.opentracing.PendingTrace
import datadog.trace.api.sampling.PrioritySampling
import datadog.trace.common.writer.DDApi
import datadog.trace.common.writer.ListWriter
import spock.lang.Specification

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

import static datadog.trace.api.Config.DEFAULT_AGENT_HOST
import static datadog.trace.api.Config.DEFAULT_TRACE_AGENT_PORT

class DDApiIntegrationTest {
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

    def api = new DDApi(DEFAULT_AGENT_HOST, DEFAULT_TRACE_AGENT_PORT, v4())

    def endpoint = new AtomicReference<String>(null)
    def agentResponse = new AtomicReference<String>(null)

    DDApi.ResponseListener responseListener = { String receivedEndpoint, JsonNode responseJson ->
      endpoint.set(receivedEndpoint)
      agentResponse.set(responseJson.toString())
    }

    def setup() {
      api.addResponseListener(responseListener)
    }

    boolean v4() {
      return true
    }

    def "Sending traces succeeds (test #test)"() {
      expect:
      api.sendTraces(traces)
      if (v4()) {
        endpoint.get() == "http://localhost:8126/v0.4/traces"
        agentResponse.get() == '{"rate_by_service":{"service:,env:":1}}'
      }

      where:
      traces                                                                              | test
      []                                                                                  | 1
      [[], []]                                                                            | 2
      [[new DDSpan(1, CONTEXT)]]                                                          | 3
      [[new DDSpan(TimeUnit.MILLISECONDS.toMicros(System.currentTimeMillis()), CONTEXT)]] | 4
    }

    def "Sending bad trace fails (test #test)"() {
      expect:
      api.sendTraces(traces) == false

      where:
      traces         | test
      [""]           | 1
      ["", 123]      | 2
      [[:]]          | 3
      [new Object()] | 4
    }
  }

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
