/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.testing.internal.armeria.client.WebClient
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpResponse
import org.jboss.arquillian.container.test.api.Deployment
import org.jboss.arquillian.container.test.api.RunAsClient
import org.jboss.arquillian.spock.ArquillianSputnik
import org.jboss.arquillian.test.api.ArquillianResource
import org.jboss.shrinkwrap.api.ShrinkWrap
import org.jboss.shrinkwrap.api.asset.EmptyAsset
import org.jboss.shrinkwrap.api.spec.WebArchive
import org.junit.runner.RunWith
import spock.lang.Unroll
import test.CdiRestResource
import test.EjbRestResource
import test.RestApplication

import static io.opentelemetry.api.trace.SpanKind.SERVER

@RunWith(ArquillianSputnik)
@RunAsClient
abstract class ArquillianRestTest extends AgentInstrumentationSpecification {

  static WebClient client = WebClient.of()

  @ArquillianResource
  static URI url

  @Deployment
  static WebArchive createDeployment() {
    return ShrinkWrap.create(WebArchive)
      .addClass(RestApplication)
      .addClass(CdiRestResource)
      .addClass(EjbRestResource)
      .addAsWebInfResource(EmptyAsset.INSTANCE, "beans.xml")
  }

  def getContextRoot() {
    return url.getPath()
  }

  @Unroll
  def "test #path"() {
    when:
    AggregatedHttpResponse response = client.get(url.resolve(path).toString()).aggregate().join()

    then:
    response.status().code() == 200
    response.contentUtf8() == "hello"

    and:
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          name getContextRoot() + path
          kind SERVER
          hasNoParent()
        }
        span(1) {
          name className + ".hello"
          childOf span(0)
        }
      }
    }

    where:
    path                | className
    "rest-app/cdiHello" | "CdiRestResource"
    "rest-app/ejbHello" | "EjbRestResource"
  }
}
