/*
 * Copyright 2020, OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import io.opentelemetry.auto.instrumentation.api.Tags
import io.opentelemetry.auto.test.AgentTestRunner
import io.opentelemetry.auto.test.utils.PortUtils
import rmi.app.Greeter
import rmi.app.Server
import rmi.app.ServerLegacy

import java.rmi.registry.LocateRegistry
import java.rmi.server.UnicastRemoteObject

import static io.opentelemetry.auto.test.utils.TraceUtils.basicSpan
import static io.opentelemetry.auto.test.utils.TraceUtils.runUnderTrace
import static io.opentelemetry.trace.Span.Kind.CLIENT
import static io.opentelemetry.trace.Span.Kind.SERVER

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
    assertTraces(1) {
      trace(0, 3) {
        basicSpan(it, 0, "parent")
        span(1) {
          operationName "Greeter.hello"
          spanKind CLIENT
          childOf span(0)
          tags {
            "$Tags.COMPONENT" "rmi-client"
            "span.origin.type" Greeter.canonicalName
          }
        }
        span(2) {
          operationName "Server.hello"
          spanKind SERVER
          tags {
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
          operationName "Greeter.exceptional"
          spanKind CLIENT
          childOf span(0)
          errored true
          tags {
            "$Tags.COMPONENT" "rmi-client"
            "span.origin.type" Greeter.canonicalName
            errorTags(RuntimeException, String)
          }
        }
        span(2) {
          operationName "Server.exceptional"
          spanKind SERVER
          errored true
          tags {
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
    assertTraces(1) {
      trace(0, 3) {
        basicSpan(it, 0, "parent")
        span(1) {
          operationName "Greeter.hello"
          spanKind CLIENT
          childOf span(0)
          tags {
            "$Tags.COMPONENT" "rmi-client"
            "span.origin.type" Greeter.canonicalName
          }
        }
        span(2) {
          childOf span(1)
          operationName "ServerLegacy.hello"
          spanKind SERVER
          tags {
            "$Tags.COMPONENT" "rmi-server"
            "span.origin.type" server.class.canonicalName
          }
        }
      }
    }

    cleanup:
    serverRegistry.unbind(ServerLegacy.RMI_ID)
  }
}
