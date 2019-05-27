package datadog.trace.agent

import datadog.trace.agent.test.IntegrationTestUtils
import datadog.trace.api.Config
import org.junit.Rule
import org.junit.contrib.java.lang.system.RestoreSystemProperties
import spock.lang.Specification

import java.lang.reflect.Method

class JMXFetchTest extends Specification {

  @Rule
  public final RestoreSystemProperties restoreSystemProperties = new RestoreSystemProperties()

  def "test jmxfetch"() {
    setup:
    def currentContextLoader = Thread.currentThread().getContextClassLoader()
    DatagramSocket socket = new DatagramSocket(0)

    System.properties.setProperty("dd.jmxfetch.enabled", "true")
    System.properties.setProperty("dd.jmxfetch.statsd.port", Integer.toString(socket.localPort))
    // Overwrite writer type to disable console jmxfetch reporter
    System.properties.setProperty("dd.writer.type", "DDAgentWriter")

    def classLoader = IntegrationTestUtils.getJmxFetchClassLoader()
    // Have to set this so JMXFetch knows where to find resources
    Thread.currentThread().setContextClassLoader(classLoader)
    final Class<?> jmxFetchAgentClass =
      classLoader.loadClass("datadog.trace.agent.jmxfetch.JMXFetch")
    final Method jmxFetchInstallerMethod = jmxFetchAgentClass.getDeclaredMethod("run", Config)
    jmxFetchInstallerMethod.setAccessible(true)
    jmxFetchInstallerMethod.invoke(null, new Config())

    byte[] buf = new byte[1500]
    DatagramPacket packet = new DatagramPacket(buf, buf.length)
    socket.receive(packet)
    String received = new String(packet.getData(), 0, packet.getLength())

    Set<String> threads = Thread.getAllStackTraces().keySet().collect { it.name }

    expect:
    threads.contains("dd-jmx-collector")
    received.contains("jvm.")

    cleanup:
    jmxFetchInstallerMethod.setAccessible(false)
    socket.close()
    Thread.currentThread().setContextClassLoader(currentContextLoader)
  }

  def "test jmxfetch config"() {
    setup:
    names.each {
      System.setProperty("dd.jmxfetch.${it}.enabled", "$enable")
    }
    def classLoader = IntegrationTestUtils.getJmxFetchClassLoader()
    // Have to set this so JMXFetch knows where to find resources
    Thread.currentThread().setContextClassLoader(classLoader)
    final Class<?> jmxFetchAgentClass =
      classLoader.loadClass("datadog.trace.agent.jmxfetch.JMXFetch")
    final Method jmxFetchInstallerMethod = jmxFetchAgentClass.getDeclaredMethod("getInternalMetricFiles")
    jmxFetchInstallerMethod.setAccessible(true)

    expect:
    jmxFetchInstallerMethod.invoke(null).sort() == result.sort()

    cleanup:
    names.each {
      System.clearProperty("dd.integration.${it}.enabled")
    }

    where:
    names               | enable | result
    []                  | true   | []
    ["tomcat"]          | false  | []
    ["tomcat"]          | true   | ["datadog/trace/agent/jmxfetch/metricconfigs/tomcat.yaml"]
    ["kafka"]           | true   | ["datadog/trace/agent/jmxfetch/metricconfigs/kafka.yaml"]
    ["tomcat", "kafka"] | true   | ["datadog/trace/agent/jmxfetch/metricconfigs/tomcat.yaml", "datadog/trace/agent/jmxfetch/metricconfigs/kafka.yaml"]
    ["tomcat", "kafka"] | false  | []
    ["invalid"]         | true   | []
  }

}
