import datadog.trace.agent.test.AgentTestRunner
import spock.lang.Timeout

@Timeout(1)
class OSGIClassloadingTest extends AgentTestRunner {
  def "delegation property set on module load"() {
    setup:
    org.osgi.framework.Bundle.getName()

    expect:
    System.getProperty("org.osgi.framework.bootdelegation") == "io.opentracing.*,io.opentracing,datadog.slf4j.*,datadog.slf4j,datadog.trace.bootstrap.*,datadog.trace.bootstrap,datadog.trace.api.*,datadog.trace.api,datadog.trace.context.*,datadog.trace.context"
  }
}
