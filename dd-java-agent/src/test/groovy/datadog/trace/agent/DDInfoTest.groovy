package datadog.trace.agent

import datadog.opentracing.DDTraceOTInfo
import datadog.trace.api.DDTraceApiInfo
import spock.lang.Specification

class DDInfoTest extends Specification {
  def "info accessible from api"() {
    expect:
    DDTraceApiInfo.VERSION == DDTraceOTInfo.VERSION

    DDTraceApiInfo.VERSION != null
    DDTraceApiInfo.VERSION != ""
    DDTraceApiInfo.VERSION != "unknown"
    DDTraceOTInfo.VERSION != null
    DDTraceOTInfo.VERSION != ""
    DDTraceOTInfo.VERSION != "unknown"
  }
}
