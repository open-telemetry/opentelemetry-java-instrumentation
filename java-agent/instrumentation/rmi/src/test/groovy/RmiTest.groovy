import io.opentelemetry.auto.api.MoreTags
import io.opentelemetry.auto.api.SpanTypes
import io.opentelemetry.auto.instrumentation.api.Tags
import io.opentelemetry.auto.test.AgentTestRunner
import io.opentelemetry.auto.test.utils.PortUtils
import rmi.app.Greeter
import rmi.app.Server
import rmi.app.ServerLegacy

import java.rmi.registry.LocateRegistry
import java.rmi.server.UnicastRemoteObject

import static io.opentelemetry.auto.test.asserts.ListWriterAssert.assertTraces
import static io.opentelemetry.auto.test.utils.TraceUtils.basicSpan
import static io.opentelemetry.auto.test.utils.TraceUtils.runUnderTrace

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
    assertTraces(TEST_WRITER, 1) {
      trace(0, 4) {
        basicSpan(it, 0, "parent")
        span(1) {
          operationName "rmi.invoke"
          childOf span(0)
          tags {
            "$MoreTags.RESOURCE_NAME" "Greeter.hello"
            "$MoreTags.SPAN_TYPE" SpanTypes.RPC
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            "$Tags.COMPONENT" "rmi-client"
            "span.origin.type" Greeter.canonicalName
          }
        }
        span(2) {
          operationName "rmi.request"
          tags {
            "$MoreTags.RESOURCE_NAME" "Server.hello"
            "$MoreTags.SPAN_TYPE" SpanTypes.RPC
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_SERVER
            "$Tags.COMPONENT" "rmi-server"
            "span.origin.type" server.class.canonicalName
          }
        }
        span(3) {
          operationName "rmi.request"
          tags {
            "$MoreTags.RESOURCE_NAME" "Server.someMethod"
            "$MoreTags.SPAN_TYPE" SpanTypes.RPC
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_SERVER
            "$Tags.COMPONENT" "rmi-server"
            "span.origin.type" server.class.canonicalName
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
    assertTraces(TEST_WRITER, 1) {
      trace(0, 3) {
        basicSpan(it, 0, "parent", null, null, thrownException)
        span(1) {
          operationName "rmi.invoke"
          childOf span(0)
          errored true
          tags {
            "$MoreTags.RESOURCE_NAME" "Greeter.exceptional"
            "$MoreTags.SPAN_TYPE" SpanTypes.RPC
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            "$Tags.COMPONENT" "rmi-client"
            "span.origin.type" Greeter.canonicalName
            errorTags(RuntimeException, String)
          }
        }
        span(2) {
          operationName "rmi.request"
          errored true
          tags {
            "$MoreTags.RESOURCE_NAME" "Server.exceptional"
            "$MoreTags.SPAN_TYPE" SpanTypes.RPC
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_SERVER
            "$Tags.COMPONENT" "rmi-server"
            "span.origin.type" server.class.canonicalName
            errorTags(RuntimeException, String)
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
    assertTraces(TEST_WRITER, 1) {
      trace(0, 3) {
        basicSpan(it, 0, "parent")
        span(1) {
          operationName "rmi.invoke"
          childOf span(0)
          tags {
            "$MoreTags.RESOURCE_NAME" "Greeter.hello"
            "$MoreTags.SPAN_TYPE" SpanTypes.RPC
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            "$Tags.COMPONENT" "rmi-client"
            "span.origin.type" Greeter.canonicalName
          }
        }
        span(2) {
          childOf span(1)
          operationName "rmi.request"
          tags {
            "$MoreTags.RESOURCE_NAME" "ServerLegacy.hello"
            "$MoreTags.SPAN_TYPE" SpanTypes.RPC
            "$Tags.COMPONENT" "rmi-server"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_SERVER
            "span.origin.type" server.class.canonicalName
          }
        }
      }
    }

    cleanup:
    serverRegistry.unbind(ServerLegacy.RMI_ID)
  }
}
