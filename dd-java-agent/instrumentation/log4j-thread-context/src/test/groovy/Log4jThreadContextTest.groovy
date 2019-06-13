import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.agent.test.utils.ConfigUtils
import datadog.trace.api.CorrelationIdentifier
import io.opentracing.Scope
import io.opentracing.util.GlobalTracer
import org.apache.logging.log4j.ThreadContext

import java.util.concurrent.atomic.AtomicReference

class Log4jThreadContextTest extends AgentTestRunner {

  static {
      System.setProperty("dd.logs.injection", "true")
  }

  def "ThreadContext shows trace and span ids for active scope"() {
    when:
    ThreadContext.put("foo", "bar")
    Scope rootScope = GlobalTracer.get().buildSpan("root").startActive(true)

    then:
    ThreadContext.get(CorrelationIdentifier.getTraceIdKey()) == CorrelationIdentifier.getTraceId()
    ThreadContext.get(CorrelationIdentifier.getSpanIdKey()) == CorrelationIdentifier.getSpanId()
    ThreadContext.get("foo") == "bar"

    when:
    Scope childScope = GlobalTracer.get().buildSpan("child").startActive(true)

    then:
    ThreadContext.get(CorrelationIdentifier.getTraceIdKey()) == CorrelationIdentifier.getTraceId()
    ThreadContext.get(CorrelationIdentifier.getSpanIdKey()) == CorrelationIdentifier.getSpanId()
    ThreadContext.get("foo") == "bar"

    when:
    childScope.close()

    then:
    ThreadContext.get(CorrelationIdentifier.getTraceIdKey()) == CorrelationIdentifier.getTraceId()
    ThreadContext.get(CorrelationIdentifier.getSpanIdKey()) == CorrelationIdentifier.getSpanId()
    ThreadContext.get("foo") == "bar"

    when:
    rootScope.close()

    then:
    ThreadContext.get(CorrelationIdentifier.getTraceIdKey()) == null
    ThreadContext.get(CorrelationIdentifier.getSpanIdKey()) == null
    ThreadContext.get("foo") == "bar"
  }

  def "ThreadContext context scoped by thread"() {
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
        thread1TraceId.set(ThreadContext.get(CorrelationIdentifier.getTraceIdKey()))
      }
    }

    final Thread thread2 = new Thread() {
      @Override
      void run() {
        // other trace in scope
        final Scope thread2Scope = GlobalTracer.get().buildSpan("root2").startActive(true)
        try {
          thread2TraceId.set(ThreadContext.get(CorrelationIdentifier.getTraceIdKey()))
        } finally {
          thread2Scope.close()
        }
      }
    }
    final Scope mainScope = GlobalTracer.get().buildSpan("root").startActive(true)
    thread1.start()
    thread2.start()
    final String mainThreadTraceId = ThreadContext.get(CorrelationIdentifier.getTraceIdKey())
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
