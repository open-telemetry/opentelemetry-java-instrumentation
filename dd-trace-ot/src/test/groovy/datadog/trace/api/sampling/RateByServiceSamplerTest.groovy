package datadog.trace.api.sampling

import com.fasterxml.jackson.databind.ObjectMapper
import datadog.opentracing.DDSpan
import datadog.opentracing.DDSpanContext
import datadog.opentracing.DDTracer
import datadog.trace.common.sampling.PrioritySampling
import datadog.trace.common.sampling.RateByServiceSampler
import spock.lang.Specification

class RateByServiceSamplerTest extends Specification {

  def "rate by service name"() {
    setup:
    RateByServiceSampler serviceSampler = new RateByServiceSampler()
    ObjectMapper serializer = new ObjectMapper()

    when:
    String response = '{"rate_by_service": {"service:,env:":1.0, "service:spock,env:test":0.000001}}'
    serviceSampler.onResponse("traces", serializer.readTree(response))
    then:
    serviceSampler.sample(makeTrace("foo", "bar"))
    // !serviceSampler.sample(makeTrace("spock", "test"))

    when:
    response = '{"rate_by_service": {"service:,env:":0.000001, "service:spock,env:test":1.0}}'
    serviceSampler.onResponse("traces", serializer.readTree(response))
    then:
    // !serviceSampler.sample(makeTrace("foo", "bar"))
    serviceSampler.sample(makeTrace("spock", "test"))
  }

  private DDSpan makeTrace(String serviceName, String envName) {
    def context = new DDSpanContext(
      1L,
      1L,
      0L,
      serviceName,
      "fakeOperation",
      "fakeResource",
      PrioritySampling.UNSET,
      Collections.emptyMap(),
      false,
      "fakeType",
      Collections.emptyMap(),
      null,
      new DDTracer())
    context.setTag("env", envName)
    return new DDSpan(0l, context)
  }
}
