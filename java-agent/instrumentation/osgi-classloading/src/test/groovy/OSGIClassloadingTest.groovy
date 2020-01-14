import io.opentelemetry.auto.instrumentation.osgi.OSGIClassloadingInstrumentation
import io.opentelemetry.auto.test.AgentTestRunner
import org.eclipse.osgi.launch.EquinoxFactory
import org.junit.Rule
import org.junit.contrib.java.lang.system.RestoreSystemProperties
import org.osgi.framework.launch.Framework

class OSGIClassloadingTest extends AgentTestRunner {

  @Rule
  public final RestoreSystemProperties restoreSystemProperties = new RestoreSystemProperties()

  static final String BOOT_DELEGATION_ADDITION = "io.opentelemetry.auto.slf4j.*,io.opentelemetry.auto.slf4j,io.opentelemetry.auto.api.*,io.opentelemetry.auto.api,io.opentelemetry.auto.bootstrap.*,io.opentelemetry.auto.bootstrap,io.opentelemetry.auto.instrumentation.api.*,io.opentelemetry.auto.instrumentation.api,io.opentelemetry.auto.shaded.*,io.opentelemetry.auto.shaded"

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
