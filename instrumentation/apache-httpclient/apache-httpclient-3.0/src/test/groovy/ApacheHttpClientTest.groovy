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
import io.opentelemetry.auto.instrumentation.apachehttpclient.v3_0.ApacheHttpClientDecorator
import io.opentelemetry.auto.test.base.HttpClientTest
import org.apache.commons.httpclient.HostConfiguration
import org.apache.commons.httpclient.HttpClient
import org.apache.commons.httpclient.HttpMethod
import org.apache.commons.httpclient.HttpState
import org.apache.commons.httpclient.methods.GetMethod
import org.apache.commons.httpclient.methods.HeadMethod
import org.apache.commons.httpclient.methods.PostMethod
import org.apache.commons.httpclient.methods.PutMethod
import spock.lang.Shared

import java.util.concurrent.ExecutionException

abstract class ApacheHttpClientTest extends HttpClientTest<ApacheHttpClientDecorator> {
  @Shared
  def client = new HttpClient()

  @Override
  ApacheHttpClientDecorator decorator() {
    return ApacheHttpClientDecorator.DECORATE
  }

  @Override
  int doRequest(String method, URI uri, Map<String, String> headers, Closure callback) {
    def httpMethod
    switch (method) {
      case "GET":
        httpMethod = new GetMethod(uri.toString())
        break
      case "POST":
        httpMethod = new PostMethod(uri.toString())
        break
      case "PUT":
        httpMethod = new PutMethod(uri.toString())
        break
      case "HEAD":
        httpMethod = new HeadMethod(uri.toString())
        break
      default:
        throw new IllegalStateException("Unexpected http method: " + method)
    }

    headers.entrySet().each {
      httpMethod.addRequestHeader(it.key, it.value)
    }

    def statusCode = executeRequest(httpMethod, uri)
    callback?.call()
    httpMethod.releaseConnection()

    return statusCode
  }

  abstract int executeRequest(HttpMethod request, URI uri)

  @Override
  boolean testCircularRedirects() {
    // only creates 1 server request instead of 2 server requests before throwing exception like others
    false
  }

  @Override
  Integer statusOnRedirectError() {
    return 302
  }

  def "basic #method request with circular redirects"() {
    given:
    def uri = server.address.resolve("/circular-redirect")

    when:
    doRequest(method, uri)

    then:
    def ex = thrown(Exception)
    def thrownException = ex instanceof ExecutionException ? ex.cause : ex

    and:
    assertTraces(1) {
      trace(0, 2) {
        clientSpan(it, 0, null, method, false, false, uri, statusOnRedirectError(), thrownException)
        serverSpan(it, 1, span(0))
      }
    }

    where:
    method = "GET"
  }
}


class ApacheClientHttpMethod extends ApacheHttpClientTest {
  @Override
  int executeRequest(HttpMethod httpMethod, URI uri) {
    client.executeMethod(httpMethod)
  }
}

class ApacheClientHostConfiguration extends ApacheHttpClientTest {
  @Override
  int executeRequest(HttpMethod httpMethod, URI uri) {
    client.executeMethod(new HostConfiguration(), httpMethod)
  }
}

class ApacheClientHttpState extends ApacheHttpClientTest {
  @Override
  int executeRequest(HttpMethod httpMethod, URI uri) {
    client.executeMethod(new HostConfiguration(), httpMethod, new HttpState())
  }
}
