import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.api.GlobalTracer
import org.apache.catalina.WebResource
import org.apache.catalina.WebResourceRoot
import org.apache.catalina.loader.ParallelWebappClassLoader

class TomcatClassloadingTest extends AgentTestRunner {

  WebResourceRoot resources = Mock(WebResourceRoot) {
    getResource(_) >> Mock(WebResource)
    listResources(_) >> []
    // Looks like 9.x.x needs this one:
    getResources(_) >> []
  }
  ParallelWebappClassLoader classloader = new ParallelWebappClassLoader(null)

  def "tomcat class loading delegates to parent for Datadog classes"() {
    setup:
    classloader.setResources(resources)
    classloader.init()
    classloader.start()

    when:
    // If instrumentation didn't work this would blow up with NPE due to incomplete resources mocking
    def clazz = classloader.loadClass("datadog.trace.api.GlobalTracer")

    then:
    clazz == GlobalTracer
  }
}
