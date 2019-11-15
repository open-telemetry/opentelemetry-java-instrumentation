import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.instrumentation.osgi.OSGIClassloadingInstrumentation
import org.eclipse.osgi.launch.EquinoxFactory
import org.junit.Rule
import org.junit.contrib.java.lang.system.RestoreSystemProperties
import org.osgi.framework.launch.Framework

class OSGIClassloadingTest extends AgentTestRunner {

  @Rule
  public final RestoreSystemProperties restoreSystemProperties = new RestoreSystemProperties()

  static final String BOOT_DELEGATION_ADDITION = "datadog.slf4j.*,datadog.slf4j,datadog.trace.agent.TracingAgent.*,datadog.trace.agent.TracingAgent,datadog.trace.api.*,datadog.trace.api,datadog.trace.bootstrap.*,datadog.trace.bootstrap,datadog.trace.context.*,datadog.trace.context,datadog.trace.instrumentation.api.*,datadog.trace.instrumentation.api,io.opentracing.*,io.opentracing"

  def "delegation property set on module load"() {
    when:
    org.osgi.framework.Bundle.getName()

    then:
    System.getProperty("org.osgi.framework.bootdelegation") == BOOT_DELEGATION_ADDITION
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

  def "test property transformations"() {
    when:
    def newValue = OSGIClassloadingInstrumentation.Helper.getNewValue(existingValue)

    then:
    newValue == expectedNewValue

    where:
    existingValue                                  | expectedNewValue
    null                                           | BOOT_DELEGATION_ADDITION
    ""                                             | BOOT_DELEGATION_ADDITION
    BOOT_DELEGATION_ADDITION                       | BOOT_DELEGATION_ADDITION
    "foo.*"                                        | "foo.*," + BOOT_DELEGATION_ADDITION
    "foo.*," + BOOT_DELEGATION_ADDITION + ",bar.*" | "foo.*," + BOOT_DELEGATION_ADDITION + ",bar.*"

  }
}
