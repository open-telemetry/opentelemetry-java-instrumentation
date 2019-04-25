import datadog.trace.agent.test.AgentTestRunner

class JBossClassloadingTest extends AgentTestRunner {
  def "delegation property set on module load"() {
    setup:
    org.jboss.modules.Module.getName()

    expect:
    System.getProperty("jboss.modules.system.pkgs") == "datadog.slf4j,datadog.trace.api,datadog.trace.bootstrap,datadog.trace.context,io.opentracing"
  }
}
