/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static io.opentelemetry.api.trace.SpanKind.SERVER

import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.instrumentation.test.utils.OkHttpUtils
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
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

@RunWith(ArquillianSputnik)
@RunAsClient
abstract class ArquillianRestTest extends AgentInstrumentationSpecification {

  static OkHttpClient client = OkHttpUtils.client()

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
    Request request = new Request.Builder().url(HttpUrl.get(url.resolve(path))).build()
    Response response = client.newCall(request).execute()

    then:
    response.withCloseable {
      assert response.code() == 200
      assert response.body().string() == "hello"
      true
    }

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
