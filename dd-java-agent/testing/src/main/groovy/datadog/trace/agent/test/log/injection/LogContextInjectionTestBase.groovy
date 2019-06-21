package datadog.trace.agent.test.log.injection

import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.agent.test.utils.ConfigUtils
import datadog.trace.api.CorrelationIdentifier
import io.opentracing.Scope
import io.opentracing.util.GlobalTracer

import java.util.concurrent.atomic.AtomicReference

/**
 * This class represents the standard test cases that new logging library integrations MUST
 * satisfy in order to support log injection.
 */
abstract class LogContextInjectionTestBase extends AgentTestRunner {

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
      System.setProperty("dd.logs.injection", "true")
    }
  }

  def "Log context shows trace and span ids for active scope"() {
    when:
    put("foo", "bar")
    Scope rootScope = GlobalTracer.get().buildSpan("root").startActive(true)

    then:
    get(CorrelationIdentifier.getTraceIdKey()) == CorrelationIdentifier.getTraceId()
    get(CorrelationIdentifier.getSpanIdKey()) == CorrelationIdentifier.getSpanId()
    get("foo") == "bar"

    when:
    Scope childScope = GlobalTracer.get().buildSpan("child").startActive(true)

    then:
    get(CorrelationIdentifier.getTraceIdKey()) == CorrelationIdentifier.getTraceId()
    get(CorrelationIdentifier.getSpanIdKey()) == CorrelationIdentifier.getSpanId()
    get("foo") == "bar"

    when:
    childScope.close()

    then:
    get(CorrelationIdentifier.getTraceIdKey()) == CorrelationIdentifier.getTraceId()
    get(CorrelationIdentifier.getSpanIdKey()) == CorrelationIdentifier.getSpanId()
    get("foo") == "bar"

    when:
    rootScope.close()

    then:
    get(CorrelationIdentifier.getTraceIdKey()) == null
    get(CorrelationIdentifier.getSpanIdKey()) == null
    get("foo") == "bar"
  }

  def "Log context is scoped by thread"() {
    setup:
    ConfigUtils.updateConfig {
      System.setProperty("dd.logs.injection", "true")
    }
    AtomicReference<String> thread1TraceId = new AtomicReference<>()
    AtomicReference<String> thread2TraceId = new AtomicReference<>()

    final Thread thread1 = new Thread() {
      @Override
      void run() {
        // no trace in scope
        thread1TraceId.set(get(CorrelationIdentifier.getTraceIdKey()))
      }
    }

    final Thread thread2 = new Thread() {
      @Override
      void run() {
        // other trace in scope
        final Scope thread2Scope = GlobalTracer.get().buildSpan("root2").startActive(true)
        try {
          thread2TraceId.set(get(CorrelationIdentifier.getTraceIdKey()))
        } finally {
          thread2Scope.close()
        }
      }
    }
    final Scope mainScope = GlobalTracer.get().buildSpan("root").startActive(true)
    thread1.start()
    thread2.start()
    final String mainThreadTraceId = get(CorrelationIdentifier.getTraceIdKey())
    final String expectedMainThreadTraceId = CorrelationIdentifier.getTraceId()

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
