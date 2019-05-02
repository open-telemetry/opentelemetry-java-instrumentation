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
    System.getProperty("org.osgi.framework.bootdelegation") == "datadog.slf4j.*,datadog.slf4j,datadog.trace.api.*,datadog.trace.api,datadog.trace.bootstrap.*,datadog.trace.bootstrap,datadog.trace.context.*,datadog.trace.context,io.opentracing.*,io.opentracing"
  }

  def "test OSGi framework factory"() {
    setup:
    def config = ["osgi.support.class.certificate": "false"]

    when:
    Framework framework = factory.newFramework(config)

    then:
    framework != null

    where:
    factory                                           | _
    new EquinoxFactory()                              | _
    new org.apache.felix.framework.FrameworkFactory() | _
  }
}
