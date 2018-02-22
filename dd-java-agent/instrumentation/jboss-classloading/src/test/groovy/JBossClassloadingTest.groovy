import datadog.trace.agent.test.AgentTestRunner
import spock.lang.Timeout

@Timeout(1)
class JBossClassloadingTest extends AgentTestRunner {
  def "delegation property set on module load"() {
    setup:
    org.jboss.modules.Module.getName()

    expect:
    System.getProperty("jboss.modules.system.pkgs") == "io.opentracing,datadog.slf4j,datadog.trace"
  }
}
