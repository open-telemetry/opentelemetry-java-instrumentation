package datadog.opentracing.resolver

import datadog.opentracing.DDTracer
import io.opentracing.Tracer
import io.opentracing.contrib.tracerresolver.TracerResolver
import io.opentracing.noop.NoopTracer
import io.opentracing.noop.NoopTracerFactory
import io.opentracing.util.GlobalTracer
import spock.lang.Specification

import java.lang.reflect.Field

class TracerResolverTest extends Specification {

  def setup() {
    setTracer(null)
    assert !GlobalTracer.isRegistered()
  }

  def "test resolveTracer"() {
    when:
    def tracer = TracerResolver.resolveTracer()

    then:
    !GlobalTracer.isRegistered()
    tracer instanceof DDTracer
  }

  def "test registerTracer"() {
    when:
    def tracer = DDTracerResolver.registerTracer()

    then:
    GlobalTracer.isRegistered()
    tracer instanceof DDTracer
  }

  def "test disable DDTracerResolver"() {
    setup:
    System.setProperty("dd.trace.resolver.enabled", "false")

    when:
    def tracer = TracerResolver.resolveTracer()

    then:
    !GlobalTracer.isRegistered()
    tracer == null

    when:
    tracer = DDTracerResolver.registerTracer()

    then:
    !GlobalTracer.isRegistered()
    tracer instanceof NoopTracer

    cleanup:
    System.clearProperty("dd.trace.resolver.enabled")
  }

  def setTracer(Tracer tracer) {
    final Field tracerField = GlobalTracer.getDeclaredField("tracer")
    tracerField.setAccessible(true)
    tracerField.set(tracer, NoopTracerFactory.create())
  }
}
