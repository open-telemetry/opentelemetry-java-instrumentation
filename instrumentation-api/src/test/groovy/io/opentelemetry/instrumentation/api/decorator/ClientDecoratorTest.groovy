/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.decorator

import io.opentelemetry.context.Context
import io.opentelemetry.OpenTelemetry
import io.opentelemetry.trace.Span
import io.opentelemetry.trace.Tracer
import io.opentelemetry.trace.TracingContextUtils

class ClientDecoratorTest extends BaseDecoratorTest {

  private static final Tracer TRACER = OpenTelemetry.getGlobalTracer("io.opentelemetry.auto")

  def "test afterStart"() {
    setup:
    def decorator = newDecorator((String) serviceName)

    when:
    decorator.afterStart(span)

    then:
    _ * span.setAttribute(_, _) // Want to allow other calls from child implementations.
    0 * _

    where:
    serviceName << ["test-service", "other-service", null]
  }

  def "test beforeFinish"() {
    when:
    newDecorator("test-service").beforeFinish(span)

    then:
    0 * _
  }

  def "test getOrCreateSpan when no existing client span"() {
    when:
    def span = ClientDecorator.getOrCreateSpan("test", TRACER)

    then:
    assert span.getSpanContext().isValid()
  }

  def "test getOrCreateSpan when existing client span"() {
    setup:
    def existing = ClientDecorator.getOrCreateSpan("existing", TRACER)
    def scope = ClientDecorator.currentContextWith(existing).makeCurrent()

    when:
    def span = ClientDecorator.getOrCreateSpan("test", TRACER)

    then:
    assert !span.getSpanContext().isValid()

    cleanup:
    scope.close()
  }

  def "test getOrCreateSpan internal after client span"() {
    setup:
    def client = ClientDecorator.getOrCreateSpan("existing", TRACER)
    def scope = ClientDecorator.currentContextWith(client).makeCurrent()

    when:
    def internal = TRACER.spanBuilder("internal").setSpanKind(Span.Kind.INTERNAL).startSpan()
    def scope2 = TracingContextUtils.currentContextWith(internal)

    then:
    assert internal.getSpanContext().isValid()
    assert Context.current().getValue(ClientDecorator.CONTEXT_CLIENT_SPAN_KEY) == client
    assert io.opentelemetry.trace.Span.fromContext(Context.current()) == internal

    cleanup:
    scope2.close()
    scope.close()
  }

  def "test currentContextWith"() {
    setup:
    def span = ClientDecorator.getOrCreateSpan("test", TRACER)

    when:
    def context = ClientDecorator.currentContextWith(span)

    then:
    assert context.getValue(ClientDecorator.CONTEXT_CLIENT_SPAN_KEY) == span
    assert io.opentelemetry.trace.Span.fromContext(context) == span
  }

  @Override
  def newDecorator() {
    return newDecorator("test-service")
  }

  def newDecorator(String serviceName) {
    return new ClientDecorator() {
    }
  }
}
