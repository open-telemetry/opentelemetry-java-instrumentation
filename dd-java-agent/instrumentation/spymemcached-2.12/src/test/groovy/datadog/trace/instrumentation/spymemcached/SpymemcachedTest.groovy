package datadog.trace.instrumentation.spymemcached

import datadog.trace.agent.test.AgentTestRunner
import net.spy.memcached.MemcachedClient
import org.testcontainers.containers.GenericContainer
import spock.lang.Shared

class SpymemcachedTest extends AgentTestRunner {

  @Shared
  def defaultMemcachedPort = 11211
  @Shared
  /*
    Note: type here has to stay undefined, otherwise tests will fail in CI in Java 7 because
    'testcontainers' are built for Java 8 and Java 7 cannot load this class.
   */
  def memcachedContainer

  @Shared
  MemcachedClient memcached

  def setupSpec() {
    // Setup default hostname and port
    String ip = "127.0.0.1"
    int port = defaultMemcachedPort

    /*
      CI will provide us with memcached container running along side our build.
      When building locally, however, we need to take matters into our own hands
      and we use 'testcontainers' for this.
     */
    if ("true" != System.getenv("CI")) {
      memcachedContainer = new GenericContainer('memcached:latest')
        .withExposedPorts(defaultMemcachedPort)
      memcachedContainer.start()
      ip = memcachedContainer.containerIpAddress
      port = memcachedContainer.getMappedPort(defaultMemcachedPort)
    }

    memcached = new MemcachedClient(new InetSocketAddress(ip, port))
  }

  def cleanupSpec() {
    if (memcachedContainer) {
      memcachedContainer.stop()
    }
  }

  def "command with no arguments"() {
    when:
    memcached.set("foo", 3600, "bar").get()

    then:
    memcached.get("foo") == "bar"
  }
}
