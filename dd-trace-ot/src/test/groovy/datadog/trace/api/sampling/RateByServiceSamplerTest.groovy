package datadog.trace.api.sampling


import datadog.opentracing.DDSpan
import datadog.opentracing.DDSpanContext
import datadog.opentracing.DDTracer
import datadog.opentracing.SpanFactory
import datadog.trace.api.DDTags
import datadog.trace.common.sampling.RateByServiceSampler
import datadog.trace.common.writer.LoggingWriter
import datadog.trace.common.writer.ddagent.DDAgentApi
import datadog.trace.util.test.DDSpecification

import static datadog.trace.common.sampling.RateByServiceSampler.DEFAULT_KEY

class RateByServiceSamplerTest extends DDSpecification {
  static serializer = DDAgentApi.RESPONSE_ADAPTER

  def "invalid rate -> 1"() {
    setup:
    RateByServiceSampler serviceSampler = new RateByServiceSampler()
    String response = '{"rate_by_service": {"service:,env:":' + rate + '}}'
    serviceSampler.onResponse("traces", serializer.fromJson(response))
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

    when:
    String response = '{"rate_by_service": {"service:spock,env:test":0.0}}'
    serviceSampler.onResponse("traces", serializer.fromJson(response))
    DDSpan span1 = SpanFactory.newSpanOf("foo", "bar")
    serviceSampler.setSamplingPriority(span1)
    then:
    span1.getSamplingPriority() == PrioritySampling.SAMPLER_KEEP
    serviceSampler.sample(span1)

    when:
    response = '{"rate_by_service": {"service:spock,env:test":1.0}}'
    serviceSampler.onResponse("traces", serializer.fromJson(response))
    DDSpan span2 = SpanFactory.newSpanOf("spock", "test")
    serviceSampler.setSamplingPriority(span2)
    then:
    span2.getSamplingPriority() == PrioritySampling.SAMPLER_KEEP
    serviceSampler.sample(span2)
  }

  def "sampling priority set on context"() {
    setup:
    RateByServiceSampler serviceSampler = new RateByServiceSampler()
    String response = '{"rate_by_service": {"service:,env:":1.0}}'
    serviceSampler.onResponse("traces", serializer.fromJson(response))

    DDSpan span = SpanFactory.newSpanOf("foo", "bar")
    serviceSampler.setSamplingPriority(span)
    expect:
    // sets correctly on root span
    span.getSamplingPriority() == PrioritySampling.SAMPLER_KEEP
    // RateByServiceSamler must not set the sample rate
    span.getMetrics().get(DDSpanContext.SAMPLE_RATE_KEY) == null
  }

  def "sampling priority set when service later"() {
    def sampler = new RateByServiceSampler()
    def tracer = DDTracer.builder().writer(new LoggingWriter()).sampler(sampler).build()

    sampler.onResponse("test", serializer
      .fromJson('{"rate_by_service":{"service:,env:":1.0,"service:spock,env:":0.0}}'))

    when:
    def span = tracer.buildSpan("test").start()

    then:
    span.getSamplingPriority() == null

    when:
    span.setTag(DDTags.SERVICE_NAME, "spock")

    then:
    span.finish()
    span.getSamplingPriority() == PrioritySampling.SAMPLER_DROP

    when:
    span = tracer.buildSpan("test").withTag(DDTags.SERVICE_NAME, "spock").start()
    span.finish()

    then:
    span.getSamplingPriority() == PrioritySampling.SAMPLER_DROP
  }

  def "setting forced tracing via tag"() {
    when:
    def sampler = new RateByServiceSampler()
    def tracer = DDTracer.builder().writer(new LoggingWriter()).sampler(sampler).build()
    def span = tracer.buildSpan("root").start()
    if (tagName) {
      span.setTag(tagName, tagValue)
    }
    span.finish()

    then:
    span.getSamplingPriority() == expectedPriority

    where:
    tagName       | tagValue | expectedPriority
    'manual.drop' | true     | PrioritySampling.USER_DROP
    'manual.keep' | true     | PrioritySampling.USER_KEEP
  }

  def "not setting forced tracing via tag or setting it wrong value not causing exception"() {
    setup:
    def sampler = new RateByServiceSampler()
    def tracer = DDTracer.builder().writer(new LoggingWriter()).sampler(sampler).build()
    def span = tracer.buildSpan("root").start()
    if (tagName) {
      span.setTag(tagName, tagValue)
    }

    expect:
    span.getSamplingPriority() == null

    cleanup:
    span.finish()

    where:
    tagName       | tagValue
    // When no tag is set default to
    null          | null
    // Setting to not known value
    'manual.drop' | false
    'manual.keep' | false
    'manual.drop' | 1
    'manual.keep' | 1
  }
}
