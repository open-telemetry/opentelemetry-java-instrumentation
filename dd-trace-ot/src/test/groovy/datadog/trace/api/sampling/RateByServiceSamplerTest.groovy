package datadog.trace.api.sampling

import com.fasterxml.jackson.databind.ObjectMapper
import datadog.opentracing.DDSpan
import datadog.opentracing.SpanFactory
import datadog.trace.common.sampling.RateByServiceSampler
import datadog.trace.util.test.DDSpecification

import static datadog.trace.common.sampling.RateByServiceSampler.DEFAULT_KEY

class RateByServiceSamplerTest extends DDSpecification {

  def "invalid rate -> 1"() {
    setup:
    RateByServiceSampler serviceSampler = new RateByServiceSampler()
    ObjectMapper serializer = new ObjectMapper()
    String response = '{"rate_by_service": {"service:,env:":' + rate + '}}'
    serviceSampler.onResponse("traces", serializer.readTree(response))
    expect:
    serviceSampler.serviceRates[DEFAULT_KEY].sampleRate == expectedRate

    where:
    rate | expectedRate
    null | 1
    1    | 1
    0    | 0.0
    -5   | 1
    5    | 1
    0.5  | 0.5
  }

  def "rate by service name"() {
    setup:
    RateByServiceSampler serviceSampler = new RateByServiceSampler()
    ObjectMapper serializer = new ObjectMapper()

    when:
    String response = '{"rate_by_service": {"service:spock,env:test":0.0}}'
    serviceSampler.onResponse("traces", serializer.readTree(response))
    DDSpan span1 = SpanFactory.newSpanOf("foo", "bar")
    serviceSampler.initializeSamplingPriority(span1)
    then:
    span1.getSamplingPriority() == PrioritySampling.SAMPLER_KEEP
    serviceSampler.sample(span1)

    when:
    response = '{"rate_by_service": {"service:spock,env:test":1.0}}'
    serviceSampler.onResponse("traces", serializer.readTree(response))
    DDSpan span2 = SpanFactory.newSpanOf("spock", "test")
    serviceSampler.initializeSamplingPriority(span2)
    then:
    span2.getSamplingPriority() == PrioritySampling.SAMPLER_KEEP
    serviceSampler.sample(span2)
  }

  def "sampling priority set on context"() {
    setup:
    RateByServiceSampler serviceSampler = new RateByServiceSampler()
    ObjectMapper serializer = new ObjectMapper()
    String response = '{"rate_by_service": {"service:,env:":1.0}}'
    serviceSampler.onResponse("traces", serializer.readTree(response))

    DDSpan span = SpanFactory.newSpanOf("foo", "bar")
    serviceSampler.initializeSamplingPriority(span)
    expect:
    // sets correctly on root span
    span.getSamplingPriority() == PrioritySampling.SAMPLER_KEEP
    // RateByServiceSamler must not set the sample rate
    span.getMetrics().get("_sample_rate") == null
  }
}
