/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static io.opentelemetry.api.trace.StatusCode.ERROR

import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.instrumentation.test.utils.PortUtils
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import org.springframework.boot.SpringApplication
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.remoting.rmi.RmiProxyFactoryBean
import org.springframework.remoting.rmi.RmiServiceExporter
import org.springframework.remoting.support.RemoteExporter
import spock.lang.Shared
import springrmi.app.SpringRmiGreeter
import springrmi.app.SpringRmiGreeterImpl

class SpringRmiTest extends AgentInstrumentationSpecification {

  @Shared
  ConfigurableApplicationContext serverAppContext

  @Shared
  ConfigurableApplicationContext clientAppContext

  @Shared
  static int registryPort

  static class ServerConfig {
    @Bean
    static RemoteExporter registerRMIExporter() {
      RmiServiceExporter exporter = new RmiServiceExporter()
      exporter.setServiceName("springRmiGreeter")
      exporter.setServiceInterface(SpringRmiGreeter)
      exporter.setService(new SpringRmiGreeterImpl())
      exporter.setRegistryPort(registryPort)
      return exporter
    }
  }

  static class ClientConfig {
    @Bean
    static RmiProxyFactoryBean rmiProxy() {
      RmiProxyFactoryBean bean = new RmiProxyFactoryBean()
      bean.setServiceInterface(SpringRmiGreeter)
      bean.setServiceUrl("rmi://localhost:" + registryPort + "/springRmiGreeter")
      return bean
    }
  }

  def setupSpec() {
    registryPort = PortUtils.findOpenPort()
    def serverApp = new SpringApplication(ServerConfig)
    serverAppContext = serverApp.run()
    def clientApp = new SpringApplication(ClientConfig)
    clientAppContext = clientApp.run()
  }

  def cleanupSpec() {
    serverAppContext.close()
    clientAppContext.close()
  }

  def "Client call creates spans"() {
    given:
    SpringRmiGreeter client = clientAppContext.getBean(SpringRmiGreeter)
    when:
    def response = runWithSpan("parent") { client.hello("Test Name") }
    then:
    response == "Hello Test Name"
    assertTraces(1) {
      trace(0, 3) {
        span(0) {
          name "parent"
          kind SpanKind.INTERNAL
          hasNoParent()
        }
        span(1) {
          name "springrmi.app.SpringRmiGreeter/hello"
          kind SpanKind.CLIENT
          childOf span(0)
          attributes {
            "$SemanticAttributes.RPC_SYSTEM" "spring_rmi"
            "$SemanticAttributes.RPC_SERVICE" "springrmi.app.SpringRmiGreeter"
            "$SemanticAttributes.RPC_METHOD" "hello"
          }
        }
        span(2) {
          name "springrmi.app.SpringRmiGreeterImpl/hello"
          kind SpanKind.SERVER
          childOf span(1)
          attributes {
            "$SemanticAttributes.RPC_SYSTEM" "spring_rmi"
            "$SemanticAttributes.RPC_SERVICE" "springrmi.app.SpringRmiGreeterImpl"
            "$SemanticAttributes.RPC_METHOD" "hello"
          }
        }
      }
    }
  }

  def "Throws exception"() {
    given:
    SpringRmiGreeter client = clientAppContext.getBean(SpringRmiGreeter)
    when:
    runWithSpan("parent") { client.exceptional() }
    then:
    def error = thrown(IllegalStateException)
    assertTraces(1) {
      trace(0, 3) {
        span(0) {
          name "parent"
          kind SpanKind.INTERNAL
          status ERROR
          hasNoParent()
          event(0) {
            eventName("$SemanticAttributes.EXCEPTION_EVENT_NAME")
            attributes {
              "$SemanticAttributes.EXCEPTION_TYPE" error.getClass().getCanonicalName()
              "$SemanticAttributes.EXCEPTION_MESSAGE" error.getMessage()
              "$SemanticAttributes.EXCEPTION_STACKTRACE" String
            }
          }
        }
        span(1) {
          name "springrmi.app.SpringRmiGreeter/exceptional"
          kind SpanKind.CLIENT
          status ERROR
          childOf span(0)
          event(0) {
            eventName("$SemanticAttributes.EXCEPTION_EVENT_NAME")
            attributes {
              "$SemanticAttributes.EXCEPTION_TYPE" error.getClass().getCanonicalName()
              "$SemanticAttributes.EXCEPTION_MESSAGE" error.getMessage()
              "$SemanticAttributes.EXCEPTION_STACKTRACE" String
            }
          }
          attributes {
            "$SemanticAttributes.RPC_SYSTEM" "spring_rmi"
            "$SemanticAttributes.RPC_SERVICE" "springrmi.app.SpringRmiGreeter"
            "$SemanticAttributes.RPC_METHOD" "exceptional"
          }
        }
        span(2) {
          name "springrmi.app.SpringRmiGreeterImpl/exceptional"
          kind SpanKind.SERVER
          childOf span(1)
          status ERROR
          event(0) {
            eventName("$SemanticAttributes.EXCEPTION_EVENT_NAME")
            attributes {
              "$SemanticAttributes.EXCEPTION_TYPE" error.getClass().getCanonicalName()
              "$SemanticAttributes.EXCEPTION_MESSAGE" error.getMessage()
              "$SemanticAttributes.EXCEPTION_STACKTRACE" String
            }
          }
          attributes {
            "$SemanticAttributes.RPC_SYSTEM" "spring_rmi"
            "$SemanticAttributes.RPC_SERVICE" "springrmi.app.SpringRmiGreeterImpl"
            "$SemanticAttributes.RPC_METHOD" "exceptional"
          }
        }
      }
    }
  }

}
