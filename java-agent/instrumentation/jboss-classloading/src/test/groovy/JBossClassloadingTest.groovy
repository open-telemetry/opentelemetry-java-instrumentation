import io.opentelemetry.auto.test.AgentTestRunner
import io.opentelemetry.auto.tooling.Constants

class JBossClassloadingTest extends AgentTestRunner {
  def "delegation property set on module load"() {
    setup:
    org.jboss.modules.Module.getName()

    expect:
    assert Arrays.asList(System.getProperty("jboss.modules.system.pkgs").split(",")).containsAll(Constants.BOOTSTRAP_PACKAGE_PREFIXES)
  }
}
