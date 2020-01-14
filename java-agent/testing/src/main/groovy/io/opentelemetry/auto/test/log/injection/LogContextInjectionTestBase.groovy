package io.opentelemetry.auto.test.log.injection

import io.opentelemetry.OpenTelemetry
import io.opentelemetry.auto.instrumentation.api.AgentScope
import io.opentelemetry.auto.instrumentation.api.AgentSpan
import io.opentelemetry.auto.test.AgentTestRunner
import io.opentelemetry.auto.test.utils.ConfigUtils
import io.opentelemetry.trace.Tracer

import java.util.concurrent.atomic.AtomicReference

import static io.opentelemetry.auto.instrumentation.api.AgentTracer.activateSpan
import static io.opentelemetry.auto.instrumentation.api.AgentTracer.startSpan

/**
 * This class represents the standard test cases that new logging library integrations MUST
 * satisfy in order to support log injection.
 */
abstract class LogContextInjectionTestBase extends AgentTestRunner {

  final Tracer tracer = OpenTelemetry.getTracerFactory().get("io.opentelemetry.auto.test")

  /**
   * Set in the framework-specific context the given value at the given key
   */
  abstract put(String key, Object value)

  /**
   * Get from the framework-specific context the value at the given key
   */
  abstract get(String key)

  static {
    ConfigUtils.updateConfig {
      System.setProperty("opentelemetry.auto.logs.injection", "true")
    }
  }

  def "Log context shows trace and span ids for active scope"() {
    when:
    put("foo", "bar")
    AgentSpan rootSpan = startSpan("root")
    AgentScope rootScope = activateSpan(rootSpan, true)

    then:
    get("ot.trace_id") == tracer.getCurrentSpan().getContext().getTraceId().toLowerBase16()
    get("ot.span_id") == tracer.getCurrentSpan().getContext().getSpanId().toLowerBase16()
    get("foo") == "bar"

    when:
    AgentSpan childSpan = startSpan("child")
    AgentScope childScope = activateSpan(childSpan, true)

    then:
    get("ot.trace_id") == tracer.getCurrentSpan().getContext().getTraceId().toLowerBase16()
    get("ot.span_id") == tracer.getCurrentSpan().getContext().getSpanId().toLowerBase16()
    get("foo") == "bar"

    when:
    childScope.close()

    then:
    get("ot.trace_id") == tracer.getCurrentSpan().getContext().getTraceId().toLowerBase16()
    get("ot.span_id") == tracer.getCurrentSpan().getContext().getSpanId().toLowerBase16()
    get("foo") == "bar"

    when:
    rootScope.close()

    then:
    get("ot.trace_id") == null
    get("ot.span_id") == null
    get("foo") == "bar"
  }

  def "Log context is scoped by thread"() {
    setup:
    ConfigUtils.updateConfig {
      System.setProperty("opentelemetry.auto.logs.injection", "true")
    }
    AtomicReference<String> thread1TraceId = new AtomicReference<>()
    AtomicReference<String> thread2TraceId = new AtomicReference<>()

    final Thread thread1 = new Thread() {
      @Override
      void run() {
        // no trace in scope
        thread1TraceId.set(get("ot.trace_id"))
      }
    }

    final Thread thread2 = new Thread() {
      @Override
      void run() {
        // other trace in scope
        final AgentSpan thread2Span = startSpan("root2")
        final AgentScope thread2Scope = activateSpan(thread2Span, true)
        try {
          thread2TraceId.set(get("ot.trace_id"))
        } finally {
          thread2Scope.close()
        }
      }
    }
    final AgentSpan mainSpan = startSpan("root")
    final AgentScope mainScope = activateSpan(mainSpan, true)
    thread1.start()
    thread2.start()
    final String mainThreadTraceId = get("ot.trace_id")
    final String expectedMainThreadTraceId = tracer.getCurrentSpan().getContext().getTraceId().toLowerBase16()

    thread1.join()
    thread2.join()

    expect:
    mainThreadTraceId == expectedMainThreadTraceId
    thread1TraceId.get() == null
    thread2TraceId.get() != null
    thread2TraceId.get() != mainThreadTraceId

    cleanup:
    mainScope?.close()
  }
}
