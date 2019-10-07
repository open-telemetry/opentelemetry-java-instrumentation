package datadog.opentracing.resolver

import datadog.opentracing.DDTracer
import datadog.trace.api.Config
import datadog.trace.util.test.DDSpecification
import io.opentracing.contrib.tracerresolver.TracerResolver

class DDTracerResolverTest extends DDSpecification {

  def resolver = new DDTracerResolver()

  def "test resolveTracer"() {
    when:
    def tracer = TracerResolver.resolveTracer()

    then:
    tracer instanceof DDTracer
  }

  def "test disable DDTracerResolver"() {
    setup:
    System.setProperty("dd.trace.resolver.enabled", "false")

    when:
    def tracer = resolver.resolve(new Config())

    then:
    tracer == null

    cleanup:
    System.clearProperty("dd.trace.resolver.enabled")
  }

}
