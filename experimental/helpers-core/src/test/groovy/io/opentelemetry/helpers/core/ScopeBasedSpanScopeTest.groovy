package io.opentelemetry.helpers.core

import io.opentelemetry.OpenTelemetry
import io.opentelemetry.context.Scope
import io.opentelemetry.distributedcontext.DistributedContext
import io.opentelemetry.metrics.Meter
import io.opentelemetry.trace.Span
import io.opentelemetry.trace.Status
import io.opentelemetry.trace.Tracer
import spock.lang.Specification

class ScopeBasedSpanScopeTest extends Specification {

  def "should wrap provided data"() {
    given:
    Tracer tracer = OpenTelemetry.tracerFactory.get("io.opentelemetry.helpers", "1.0");
    long startTimestamp = System.nanoTime();
    Span span = tracer.spanBuilder("spock-test")
      .setSpanKind(Span.Kind.CLIENT)
      .setStartTimestamp(startTimestamp)
      .startSpan();
    Scope scope = tracer.withSpan(span);
    DistributedContext distributedContext = OpenTelemetry.distributedContextManager
      .contextBuilder()
      .build();
    Meter meter = OpenTelemetry.meterFactory.get("io.opentelemetry.helpers", "1.0")
    InetAddress address = InetAddress.loopbackAddress
    Map<String, Object> request = new HashMap<>();
    request.put("content", "this is a test request")
    Map<String, Object> response = new HashMap<>();
    response.put("content", "this is a test response")
    when:
    ScopeBasedSpanScope<Map<String, Object>, Map<String, Object>> spanContext =
      new ScopeBasedSpanScope<>(span, scope, startTimestamp, distributedContext,
        null, null, meter,
        null, null, null)
    spanContext.onPeerConnection(address)
    spanContext.onMessageSent(request)
    spanContext.onMessageReceived(response)
    spanContext.onSuccess(response)
    spanContext.close()
    then:
    span == spanContext.span
    distributedContext == spanContext.correlationContext
    span.status == Status.OK
  }

}
