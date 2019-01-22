package datadog.trace.tracer.writer

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.http.Request
import com.github.tomakehurst.wiremock.junit.WireMockRule
import com.github.tomakehurst.wiremock.matching.MatchResult
import datadog.trace.tracer.Clock
import datadog.trace.tracer.JsonSpan
import datadog.trace.tracer.SpanContextImpl
import datadog.trace.tracer.Trace
import datadog.trace.tracer.TraceImpl
import datadog.trace.tracer.Tracer
import datadog.trace.tracer.sampling.Sampler
import org.junit.Rule
import org.msgpack.jackson.dataformat.MessagePackFactory
import spock.lang.Specification

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo
import static com.github.tomakehurst.wiremock.client.WireMock.put
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import static com.github.tomakehurst.wiremock.client.WireMock.verify
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig

class AgentClientTest extends Specification {

  private static final int TRACE_COUNT = 10
  private static final String SERVICE_NAME = "service.name"

  private final ObjectMapper objectMapper = new ObjectMapper(new MessagePackFactory())

  def writer = Mock(Writer)
  def sampler = Mock(Sampler) {
    sample(_) >> true
  }
  def tracer = Mock(Tracer) {
    getDefaultServiceName() >> SERVICE_NAME
    getInterceptors() >> []
    getWriter() >> writer
    getSampler() >> sampler
  }

  @Rule
  public WireMockRule wireMockRule = new WireMockRule(wireMockConfig().dynamicPort().dynamicHttpsPort())

  private AgentClient client

  def setup() {
    client = new AgentClient("localhost", wireMockRule.port())

    def response = ["rate_by_service": ["test": 0.1, "another test": 0.2]]

    stubFor(put(urlEqualTo(AgentClient.TRACES_ENDPOINT))
      .willReturn(aResponse()
      .withStatus(200)
      .withHeader("Content-Type", "application/msgpack")
      .withBody(objectMapper.writeValueAsBytes(response))))
  }

  def "test send traces"() {
    setup:
    def traces = [createTrace("123"), createTrace("312")]

    when:
    def response = client.sendTraces(traces, TRACE_COUNT)

    then: "got expected response"
    response.getRate("test") == 0.1d
    response.getRate("another test") == 0.2d
    response.getRate("doesn't exist") == null
    and: "request got expected parameters"
    byte[] requestBody = null
    verify(putRequestedFor(urlEqualTo(AgentClient.TRACES_ENDPOINT))
      .withHeader(AgentClient.CONTENT_TYPE, equalTo(AgentClient.MSGPACK))
      .withHeader(AgentClient.DATADOG_META_LANG, equalTo("java"))
      // TODO: fill in these headers
      // .withHeader(AgentClient.DATADOG_META_LANG_VERSION, equalTo("java"))
      // .withHeader(AgentClient.DATADOG_META_LANG_INTERPRETER, equalTo("java"))
      // .withHeader(AgentClient.DATADOG_META_TRACER_VERSION, equalTo("java"))
      .withHeader(AgentClient.X_DATADOG_TRACE_COUNT, equalTo(Integer.toString(TRACE_COUNT)))
      .andMatching({ Request request ->
      requestBody = request.getBody()
      MatchResult.of(true)
      }))
    objectMapper.readValue(requestBody, new TypeReference<List<List<JsonSpan>>>() {}) == traces.collect {
      it.getSpans().collect { new JsonSpan(it) }
    }
  }

  def "test send empty list"() {
    when:
    def response = client.sendTraces([], TRACE_COUNT)

    then: "got expected response"
    response.getRate("test") == 0.1d
    response.getRate("another test") == 0.2d
    response.getRate("doesn't exist") == null
    and: "request got expected parameters"
    verify(putRequestedFor(urlEqualTo(AgentClient.TRACES_ENDPOINT))
      .withHeader(AgentClient.CONTENT_TYPE, equalTo(AgentClient.MSGPACK))
      .withHeader(AgentClient.DATADOG_META_LANG, equalTo("java"))
      // TODO: fill in these headers
      // .withHeader(AgentClient.DATADOG_META_LANG_VERSION, equalTo("java"))
      // .withHeader(AgentClient.DATADOG_META_LANG_INTERPRETER, equalTo("java"))
      // .withHeader(AgentClient.DATADOG_META_TRACER_VERSION, equalTo("java"))
      .withHeader(AgentClient.X_DATADOG_TRACE_COUNT, equalTo(Integer.toString(TRACE_COUNT)))
      .andMatching({ Request request ->
      MatchResult.of(objectMapper.readValue(request.getBody(), new TypeReference<List<List<JsonSpan>>>() {}) == [])
    }))
  }

  def "test failure"() {
    setup:
    stubFor(put(urlEqualTo(AgentClient.TRACES_ENDPOINT))
      .willReturn(aResponse()
      .withStatus(500)))
    def trace = createTrace("123")

    when:
    def response = client.sendTraces([trace], TRACE_COUNT)

    then:
    response == null
  }

  def "test timeout"() {
    setup:
    stubFor(put(urlEqualTo(AgentClient.TRACES_ENDPOINT))
      .willReturn(aResponse()
      .withStatus(200)
      .withChunkedDribbleDelay(5, AgentClient.READ_TIMEOUT * 2)))
    def trace = createTrace("123")

    when:
    def response = client.sendTraces([trace], TRACE_COUNT)

    then:
    response == null
  }


  def "test invalid url"() {
    when:
    client = new AgentClient("localhost", -100)

    then:
    thrown RuntimeException
  }

  Trace createTrace(String traceId) {
    def clock = new Clock(tracer)
    def parentContext = new SpanContextImpl("123", "456", "789")
    def trace = new TraceImpl(tracer, parentContext, clock.createCurrentTimestamp())
    trace.getRootSpan().setResource("test resource")
    trace.getRootSpan().setType("test type")
    trace.getRootSpan().setName("test name")
    trace.getRootSpan().setMeta("number.key", 123)
    trace.getRootSpan().setMeta("string.key", "meta string")
    trace.getRootSpan().setMeta("boolean.key", true)

    def childSpan = trace.createSpan(trace.getRootSpan().getContext())
    childSpan.setResource("child span test resource")
    childSpan.setType("child span test type")
    childSpan.setName("child span test name")
    childSpan.setMeta("child.span.number.key", 234)
    childSpan.setMeta("child.span.string.key", "new meta string")
    childSpan.setMeta("child.span.boolean.key", true)
    childSpan.finish()

    trace.getRootSpan().finish()
    return trace
  }

}
