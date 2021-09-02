/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxws.jws.v1_1

import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import io.opentelemetry.test.WebServiceClass
import io.opentelemetry.test.WebServiceDefinitionInterface
import io.opentelemetry.test.WebServiceFromInterface

import java.lang.reflect.Proxy

class JwsAnnotationsTest extends AgentInstrumentationSpecification {

  def "WebService on a class generates spans only for public methods"() {
    when:
    new WebServiceClass().doSomethingPublic()
    new WebServiceClass().doSomethingPackagePrivate()
    new WebServiceClass().doSomethingProtected()

    then:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "WebServiceClass.doSomethingPublic"
          attributes {
            "${SemanticAttributes.CODE_NAMESPACE.key}" "io.opentelemetry.test.WebServiceClass"
            "${SemanticAttributes.CODE_FUNCTION.key}" "doSomethingPublic"
          }
        }
      }
    }
  }

  def "WebService via interface generates spans only for methods of the interface"() {
    when:
    new WebServiceFromInterface().partOfPublicInterface()
    new WebServiceFromInterface().notPartOfPublicInterface()
    new WebServiceFromInterface().notPartOfAnything()

    then:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "WebServiceFromInterface.partOfPublicInterface"
          attributes {
            "${SemanticAttributes.CODE_NAMESPACE.key}" "io.opentelemetry.test.WebServiceFromInterface"
            "${SemanticAttributes.CODE_FUNCTION.key}" "partOfPublicInterface"
          }
        }
      }
    }
  }

  def "WebService via proxy must have span attributes from actual implementation"() {
    when:
    WebServiceDefinitionInterface proxy =
      Proxy.newProxyInstance(
        WebServiceFromInterface.getClassLoader(),
        [WebServiceDefinitionInterface] as Class[],
        new ProxyInvocationHandler(new WebServiceFromInterface())) as WebServiceDefinitionInterface
    proxy.partOfPublicInterface()

    then:
    proxy.getClass() != WebServiceFromInterface
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "WebServiceFromInterface.partOfPublicInterface"
          attributes {
            "${SemanticAttributes.CODE_NAMESPACE.key}" "io.opentelemetry.test.WebServiceFromInterface"
            "${SemanticAttributes.CODE_FUNCTION.key}" "partOfPublicInterface"
          }
        }
      }
    }
  }
}
