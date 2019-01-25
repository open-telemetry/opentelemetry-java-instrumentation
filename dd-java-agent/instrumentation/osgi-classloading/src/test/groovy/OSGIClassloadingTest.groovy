import datadog.trace.agent.test.AgentTestRunner
import org.eclipse.osgi.launch.EquinoxFactory
import org.junit.Rule
import org.junit.contrib.java.lang.system.RestoreSystemProperties
import org.osgi.framework.launch.Framework
import org.osgi.framework.launch.FrameworkFactory

class OSGIClassloadingTest extends AgentTestRunner {

  @Rule
  public final RestoreSystemProperties restoreSystemProperties = new RestoreSystemProperties()

  def "delegation property set on module load"() {
    when:
    org.osgi.framework.Bundle.getName()

    then:
    System.getProperty("org.osgi.framework.bootdelegation") == "io.opentracing.*,io.opentracing,datadog.slf4j.*,datadog.slf4j,datadog.trace.bootstrap.*,datadog.trace.bootstrap,datadog.trace.api.*,datadog.trace.api,datadog.trace.context.*,datadog.trace.context"
  }

  def "test Eclipse OSGi framework factory"() {
    setup:
    def config = ["osgi.support.class.certificate": "false"]
    FrameworkFactory factory = new EquinoxFactory()

    when:
    Framework framework = factory.newFramework(config)

    then:
    framework != null
  }
}
