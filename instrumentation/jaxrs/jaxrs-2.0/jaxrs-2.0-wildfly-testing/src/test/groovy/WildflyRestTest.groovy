/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import javax.ws.rs.client.Client
import javax.ws.rs.client.WebTarget
import org.glassfish.jersey.client.JerseyClientBuilder
import org.jboss.arquillian.container.test.api.Deployment
import org.jboss.arquillian.container.test.api.RunAsClient
import org.jboss.arquillian.spock.ArquillianSputnik
import org.jboss.arquillian.test.api.ArquillianResource
import org.jboss.shrinkwrap.api.ShrinkWrap
import org.jboss.shrinkwrap.api.asset.EmptyAsset
import org.jboss.shrinkwrap.api.spec.WebArchive
import org.junit.runner.RunWith
import test.CdiRestResource
import test.EjbRestResource
import test.RestApplication

@RunWith(ArquillianSputnik)
@RunAsClient
class WildflyRestTest extends AgentInstrumentationSpecification {

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

  def "test #path"() {
    when:
    Client client = JerseyClientBuilder.newClient()
    WebTarget webTarget = client.target(url)

    String result = webTarget.path(path)
      .request()
      .get()
      .readEntity(String)

    then:
    result == "hello"

    and:
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          name getContextRoot() + path
          hasNoParent()
        }
        span(1) {
          name className + ".hello"
          childOf span(0)
        }
      }
    }

    where:
    path        | className
    "cdiHello" | "CdiRestResource"
    "ejbHello" | "EjbRestResource"
  }
}
