/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static io.opentelemetry.api.trace.SpanKind.CLIENT
import static io.opentelemetry.api.trace.SpanKind.SERVER
import static io.opentelemetry.instrumentation.test.utils.TraceUtils.basicSpan
import static io.opentelemetry.instrumentation.test.utils.TraceUtils.runUnderTrace

import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.instrumentation.test.utils.PortUtils
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import java.rmi.registry.LocateRegistry
import java.rmi.server.UnicastRemoteObject
import rmi.app.Greeter
import rmi.app.Server
import rmi.app.ServerLegacy

class RmiTest extends AgentInstrumentationSpecification {
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
    assertTraces(1) {
      trace(0, 3) {
        basicSpan(it, 0, "parent")
        span(1) {
          name "rmi.app.Greeter/hello"
          kind CLIENT
          childOf span(0)
          attributes {
            "${SemanticAttributes.RPC_SYSTEM.key}" "java_rmi"
            "${SemanticAttributes.RPC_SERVICE.key}" "rmi.app.Greeter"
            "${SemanticAttributes.RPC_METHOD.key}" "hello"
          }
        }
        span(2) {
          name "rmi.app.Server/hello"
          kind SERVER
          attributes {
            "${SemanticAttributes.RPC_SYSTEM.key}" "java_rmi"
            "${SemanticAttributes.RPC_SERVICE.key}" "rmi.app.Server"
            "${SemanticAttributes.RPC_METHOD.key}" "hello"
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
    assertTraces(0) {}

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
    assertTraces(1) {
      trace(0, 3) {
        basicSpan(it, 0, "parent", null, thrownException)
        span(1) {
          name "rmi.app.Greeter/exceptional"
          kind CLIENT
          childOf span(0)
          errored true
          errorEvent(RuntimeException, String)
          attributes {
            "${SemanticAttributes.RPC_SYSTEM.key}" "java_rmi"
            "${SemanticAttributes.RPC_SERVICE.key}" "rmi.app.Greeter"
            "${SemanticAttributes.RPC_METHOD.key}" "exceptional"

          }
        }
        span(2) {
          name "rmi.app.Server/exceptional"
          kind SERVER
          errored true
          errorEvent(RuntimeException, String)
          attributes {
            "${SemanticAttributes.RPC_SYSTEM.key}" "java_rmi"
            "${SemanticAttributes.RPC_SERVICE.key}" "rmi.app.Server"
            "${SemanticAttributes.RPC_METHOD.key}" "exceptional"
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
    assertTraces(1) {
      trace(0, 3) {
        basicSpan(it, 0, "parent")
        span(1) {
          name "rmi.app.Greeter/hello"
          kind CLIENT
          childOf span(0)
          attributes {
            "${SemanticAttributes.RPC_SYSTEM.key}" "java_rmi"
            "${SemanticAttributes.RPC_SERVICE.key}" "rmi.app.Greeter"
            "${SemanticAttributes.RPC_METHOD.key}" "hello"
          }
        }
        span(2) {
          childOf span(1)
          name "rmi.app.ServerLegacy/hello"
          kind SERVER
          attributes {
            "${SemanticAttributes.RPC_SYSTEM.key}" "java_rmi"
            "${SemanticAttributes.RPC_SERVICE.key}" "rmi.app.ServerLegacy"
            "${SemanticAttributes.RPC_METHOD.key}" "hello"
          }
        }
      }
    }

    cleanup:
    serverRegistry.unbind(ServerLegacy.RMI_ID)
  }
}
