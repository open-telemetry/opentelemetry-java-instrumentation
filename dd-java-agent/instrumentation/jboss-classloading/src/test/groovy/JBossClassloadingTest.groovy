import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.agent.tooling.Constants

class JBossClassloadingTest extends AgentTestRunner {
  def "delegation property set on module load"() {
    setup:
    org.jboss.modules.Module.getName()

    expect:
    assert Arrays.asList(System.getProperty("jboss.modules.system.pkgs").split(",")).containsAll(Constants.BOOTSTRAP_PACKAGE_PREFIXES)
  }
}
