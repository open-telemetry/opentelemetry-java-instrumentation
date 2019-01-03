package datadog.trace.agent

import datadog.opentracing.DDTraceOTInfo
import datadog.trace.api.DDTraceApiInfo

class DDInfoTest {
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

  def "info accessible from agent"() {
    setup:
    def clazz = Class.forName("datadog.trace.agent.tooling.DDJavaAgentInfo")
    def versionField = clazz.getDeclaredField("VERSION")
    def version = versionField.get(null)

    expect:
    version != null
    version != ""
    version != "unknown"
    version == DDTraceApiInfo.VERSION
  }
}
