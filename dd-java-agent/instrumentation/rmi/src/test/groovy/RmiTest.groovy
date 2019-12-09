import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.agent.test.utils.PortUtils
import rmi.app.Greeter
import rmi.app.Server
import rmi.app.ServerLegacy

import java.rmi.registry.LocateRegistry
import java.rmi.server.UnicastRemoteObject

import static datadog.trace.agent.test.asserts.ListWriterAssert.assertTraces
import static datadog.trace.agent.test.utils.TraceUtils.basicSpan
import static datadog.trace.agent.test.utils.TraceUtils.runUnderTrace

class RmiTest extends AgentTestRunner {
  def registryPort = PortUtils.randomOpenPort()
  def serverRegistry = LocateRegistry.createRegistry(registryPort)
  def clientRegistry = LocateRegistry.getRegistry("localhost", registryPort)

  def cleanup() {
    UnicastRemoteObject.unexportObject(serverRegistry, true)
  }

  def "Client call creates spans"() {
    setup:
    def server = new Server()
    serverRegistry.rebind(Server.RMI_ID, server)

    when:
    def response = runUnderTrace("parent") {
      def client = (Greeter) clientRegistry.lookup(Server.RMI_ID)
      return client.hello("you")
    }

    then:
    response.contains("Hello you")
    assertTraces(TEST_WRITER, 2) {
      trace(1, 2) {
        basicSpan(it, 0, "parent")
        span(1) {
          resourceName "Greeter#hello"
          operationName "rmi.invoke"
          childOf span(0)
          tags {
            "span.origin.type" Greeter.canonicalName
            defaultTags()
          }
        }
      }
      trace(0, 1) {
        span(0) {
          resourceName "Server#hello"
          operationName "rmi.request"
          tags {
            "span.origin.type" server.class.canonicalName
            defaultTags(true)
          }
        }
      }
    }

    cleanup:
    serverRegistry.unbind("Server")
  }

  def "Calling server builtin methods doesn't create server spans"() {
    setup:
    def server = new Server()
    serverRegistry.rebind(Server.RMI_ID, server)

    when:
    server.equals(new Server())
    server.getRef()
    server.hashCode()
    server.toString()
    server.getClass()

    then:
    assertTraces(TEST_WRITER, 0) {}

    cleanup:
    serverRegistry.unbind("Server")
  }

  def "Service throws exception and its propagated to spans"() {
    setup:
    def server = new Server()
    serverRegistry.rebind(Server.RMI_ID, server)

    when:
    runUnderTrace("parent") {
      def client = (Greeter) clientRegistry.lookup(Server.RMI_ID)
      client.exceptional()
    }

    then:
    def thrownException = thrown(RuntimeException)
    assertTraces(TEST_WRITER, 2) {
      trace(1, 2) {
        basicSpan(it, 0, "parent", null, thrownException)
        span(1) {
          resourceName "Greeter#exceptional"
          operationName "rmi.invoke"
          childOf span(0)
          errored true

          tags {
            "span.origin.type" Greeter.canonicalName
            errorTags(RuntimeException, String)
            defaultTags()
          }
        }
      }
      trace(0, 1) {
        span(0) {
          resourceName "Server#exceptional"
          operationName "rmi.request"
          errored true

          tags {
            "span.origin.type" server.class.canonicalName
            errorTags(RuntimeException, String)
            defaultTags(true)
          }
        }
      }
    }

    cleanup:
    serverRegistry.unbind("Server")
  }

  def "Client call using ServerLegacy_stub creates spans"() {
    setup:
    def server = new ServerLegacy()
    serverRegistry.rebind(ServerLegacy.RMI_ID, server)

    when:
    def response = runUnderTrace("parent") {
      def client = (Greeter) clientRegistry.lookup(ServerLegacy.RMI_ID)
      return client.hello("you")
    }

    then:
    response.contains("Hello you")
    assertTraces(TEST_WRITER, 2) {
      def parentSpan = TEST_WRITER[1][1]
      trace(1, 2) {
        basicSpan(it, 0, "parent")
        span(1) {
          resourceName "Greeter#hello"
          operationName "rmi.invoke"
          childOf span(0)
          tags {
            "span.origin.type" Greeter.canonicalName
            defaultTags()
          }
        }
      }

      trace(0, 1) {
        span(0) {
          childOf parentSpan
          resourceName "ServerLegacy#hello"
          operationName "rmi.request"
          tags {
            "span.origin.type" server.class.canonicalName
            defaultTags(true)
          }
        }
      }
    }

    cleanup:
    serverRegistry.unbind(ServerLegacy.RMI_ID)
  }
}
