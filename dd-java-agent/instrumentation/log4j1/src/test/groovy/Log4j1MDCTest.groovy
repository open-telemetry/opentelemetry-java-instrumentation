import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.agent.test.utils.ConfigUtils
import datadog.trace.api.CorrelationIdentifier
import io.opentracing.Scope
import io.opentracing.util.GlobalTracer
import org.apache.log4j.MDC
import java.util.concurrent.atomic.AtomicReference

class Log4j1MDCTest extends AgentTestRunner {

  static {
    ConfigUtils.updateConfig {
      System.setProperty("dd.logs.injection", "true")
    }
  }

  def "MDC shows trace and span ids for active scope"() {

    when:
    MDC.put("foo", "bar")
    Scope rootScope = GlobalTracer.get().buildSpan("root").startActive(true)

    then:
    MDC.get(CorrelationIdentifier.getTraceIdKey()) == CorrelationIdentifier.getTraceId()
    MDC.get(CorrelationIdentifier.getSpanIdKey()) == CorrelationIdentifier.getSpanId()
    MDC.get("foo") == "bar"

    when:
    Scope childScope = GlobalTracer.get().buildSpan("child").startActive(true)

    then:
    MDC.get(CorrelationIdentifier.getTraceIdKey()) == CorrelationIdentifier.getTraceId()
    MDC.get(CorrelationIdentifier.getSpanIdKey()) == CorrelationIdentifier.getSpanId()
    MDC.get("foo") == "bar"

    when:
    childScope.close()

    then:
    MDC.get(CorrelationIdentifier.getTraceIdKey()) == CorrelationIdentifier.getTraceId()
    MDC.get(CorrelationIdentifier.getSpanIdKey()) == CorrelationIdentifier.getSpanId()
    MDC.get("foo") == "bar"

    when:
    rootScope.close()

    then:
    MDC.get(CorrelationIdentifier.getTraceIdKey()) == null
    MDC.get(CorrelationIdentifier.getSpanIdKey()) == null
    MDC.get("foo") == "bar"
  }

  def "MDC context scoped by thread"() {
    setup:
    ConfigUtils.updateConfig {
      System.setProperty("dd.logs.injection", "true")
    }
    AtomicReference<Object> thread1TraceId = new AtomicReference<>()
    AtomicReference<Object> thread2TraceId = new AtomicReference<>()

    final Thread thread1 = new Thread() {
      @Override
      void run() {
        // no trace in scope
        thread1TraceId.set(MDC.get(CorrelationIdentifier.getTraceIdKey()))
      }
    }

    final Thread thread2 = new Thread() {
      @Override
      void run() {
        // other trace in scope
        final Scope thread2Scope = GlobalTracer.get().buildSpan("root2").startActive(true)
        try {
          thread2TraceId.set(MDC.get(CorrelationIdentifier.getTraceIdKey()))
        } finally {
          thread2Scope.close()
        }
      }
    }
    final Scope mainScope = GlobalTracer.get().buildSpan("root").startActive(true)
    thread1.start()
    thread2.start()
    final String mainThreadTraceId = MDC.get(CorrelationIdentifier.getTraceIdKey())
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
