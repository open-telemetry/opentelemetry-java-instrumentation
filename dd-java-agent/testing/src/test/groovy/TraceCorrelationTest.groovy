import datadog.opentracing.DDSpan
import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.api.CorrelationIdentifier
import io.opentracing.Scope
import io.opentracing.util.GlobalTracer

class TraceCorrelationTest extends AgentTestRunner {

  def "access trace correlation only under trace"() {
    when:
    Scope scope = GlobalTracer.get().buildSpan("myspan").startActive(true)
    DDSpan span = (DDSpan) scope.span()

    then:
    CorrelationIdentifier.traceId == span.traceId
    CorrelationIdentifier.spanId == span.spanId

    when:
    scope.close()

    then:
    CorrelationIdentifier.traceId == "0"
    CorrelationIdentifier.spanId == "0"
  }
}
