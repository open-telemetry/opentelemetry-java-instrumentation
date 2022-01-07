/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */


import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import org.testcontainers.shaded.org.bouncycastle.asn1.x509.qualified.SemanticsInformation
import spock.lang.Shared

import org.springframework.boot.SpringApplication
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.remoting.rmi.RmiProxyFactoryBean
import org.springframework.context.annotation.Bean
import org.springframework.remoting.rmi.RmiServiceExporter
import org.springframework.remoting.support.RemoteExporter

import springrmi.app.SpringRmiGreeter
import springrmi.app.SpringRmiGreeterImpl

import static io.opentelemetry.api.trace.StatusCode.ERROR

class SpringRmiTest extends AgentInstrumentationSpecification{

  @Shared
  ConfigurableApplicationContext serverAppContext

  @Shared
  ConfigurableApplicationContext clientAppContext

  static class ServerConfig {
    @Bean
    RemoteExporter registerRMIExporter() {
      RmiServiceExporter exporter = new RmiServiceExporter();
      exporter.setServiceName("springRmiGreeter");
      exporter.setServiceInterface(SpringRmiGreeter.class);
      exporter.setService(new SpringRmiGreeterImpl());
      return exporter;
    }
  }

  static class ClientConfig {
    @Bean
    RmiProxyFactoryBean rmiProxy() {
      RmiProxyFactoryBean bean = new RmiProxyFactoryBean();
      bean.setServiceInterface(SpringRmiGreeter.class);
      bean.setServiceUrl("rmi://localhost:1099/springRmiGreeter");
      return bean;
    }
  }

  def setupSpec() {
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
    SpringRmiGreeter client = clientAppContext.getBean(SpringRmiGreeter.class)
    when:
    def response = runWithSpan("parent") { client.hello("Test Name") }
    then:
    response == "Hello Test Name"
    assertTraces(1) {
      trace(0, 3){
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
    SpringRmiGreeter client = clientAppContext.getBean(SpringRmiGreeter.class)
    when:
    def response = runWithSpan("parent") { client.exceptional() }
    then:
    def error = thrown(IllegalStateException)
    assertTraces(1) {
      trace(0,3) {
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
