import datadog.opentracing.DDSpan
import datadog.opentracing.DDTracer
import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.api.CorrelationIdentifier
import datadog.trace.api.GlobalTracer
import io.opentracing.Scope

class TraceCorrelationTest extends AgentTestRunner {

  def "access trace correlation only under trace"() {
    when:
    Scope scope = ((DDTracer) GlobalTracer.get()).buildSpan("myspan").startActive(true)
    DDSpan span = (DDSpan) scope.span()

    then:
    CorrelationIdentifier.traceId == span.traceId.toString()
    CorrelationIdentifier.spanId == span.spanId.toString()

    when:
    scope.close()

    then:
    CorrelationIdentifier.traceId == "0"
    CorrelationIdentifier.spanId == "0"
  }
}
