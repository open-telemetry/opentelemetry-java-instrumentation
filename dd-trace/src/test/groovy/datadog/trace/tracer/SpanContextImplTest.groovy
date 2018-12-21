package datadog.trace.tracer

import datadog.trace.api.sampling.PrioritySampling
import nl.jqno.equalsverifier.EqualsVerifier
import nl.jqno.equalsverifier.Warning
import spock.lang.Specification


class SpanContextImplTest extends Specification {

  def "test getters"() {
    when:
    def context = new SpanContextImpl("trace id", "parent id", "span id")

    then:
    context.getTraceId() == "trace id"
    context.getParentId() == "parent id"
    context.getSpanId() == "span id"

    // TODO: this still to be implemented
    context.getSamplingFlags() == PrioritySampling.SAMPLER_KEEP
  }

  def "create from parent"() {
    setup:
    def parent = new SpanContextImpl("trace id", "parent's parent id", "parent span id")

    when:
    def context = SpanContextImpl.fromParent(parent)

    then:
    context.getTraceId() == "trace id"
    context.getParentId() == "parent span id"
    context.getSpanId() ==~ /\d+/
  }

  def "create from no parent"() {
    when:
    def context = SpanContextImpl.fromParent(null)

    then:
    context.getTraceId() ==~ /\d+/
    context.getParentId() == SpanContextImpl.ZERO
    context.getSpanId() ==~ /\d+/
  }

  def "test equals"() {
    when:
    EqualsVerifier.forClass(SpanContextImpl).suppress(Warning.STRICT_INHERITANCE).verify()

    then:
    noExceptionThrown()
  }
}
