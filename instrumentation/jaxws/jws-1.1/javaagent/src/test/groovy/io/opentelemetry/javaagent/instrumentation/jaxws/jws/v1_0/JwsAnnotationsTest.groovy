/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxws.jws.v1_0

import io.opentelemetry.instrumentation.test.AgentTestRunner

class JwsAnnotationsTest extends AgentTestRunner {

  def "WebService on a class generates spans only for public methods"() {
    when:
    new WebServiceClass().doSomethingPublic()
    new WebServiceClass().doSomethingPackagePrivate()
    new WebServiceClass().doSomethingProtected()

    then:
    assertTraces(1, {
      trace(0, 1) {
        span(0) {
          name "WebServiceClass.doSomethingPublic"
          attributes {
            attribute('code.function', 'doSomethingPublic')
            attribute('code.namespace', 'io.opentelemetry.javaagent.instrumentation.jaxws.jws.v1_0.WebServiceClass')
          }
        }
      }
    })
  }

  def "WebService via interface generates spans only for methods of the interface"() {
    when:
    new WebServiceFromInterface().partOfPublicInterface()
    new WebServiceFromInterface().notPartOfPublicInterface()
    new WebServiceFromInterface().notPartOfAnything()

    then:
    assertTraces(1, {
      trace(0, 1) {
        span(0) {
          name "WebServiceFromInterface.partOfPublicInterface"
          attributes {
            attribute('code.function', 'partOfPublicInterface')
            attribute('code.namespace', 'io.opentelemetry.javaagent.instrumentation.jaxws.jws.v1_0.WebServiceFromInterface')
          }
        }
      }
    })
  }

}
