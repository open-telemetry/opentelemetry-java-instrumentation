/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.base.HttpClientTest
import org.apache.commons.httpclient.HttpClient
import org.apache.commons.httpclient.HttpMethod
import org.apache.commons.httpclient.methods.DeleteMethod
import org.apache.commons.httpclient.methods.GetMethod
import org.apache.commons.httpclient.methods.HeadMethod
import org.apache.commons.httpclient.methods.OptionsMethod
import org.apache.commons.httpclient.methods.PostMethod
import org.apache.commons.httpclient.methods.PutMethod
import org.apache.commons.httpclient.methods.TraceMethod
import spock.lang.Shared
import spock.lang.Timeout

@Timeout(5)
class CommonsHttpClientTest extends HttpClientTest implements AgentTestTrait {
  @Shared
  HttpClient client = new HttpClient()

  def setupSpec() {
    client.setConnectionTimeout(CONNECT_TIMEOUT_MS)
  }

  @Override
  boolean testCausality() {
    return false
  }

  @Override
  int doRequest(String method, URI uri, Map<String, String> headers, Closure callback) {
    HttpMethod httpMethod

    switch (method) {
      case "GET":
        httpMethod = new GetMethod(uri.toString())
        break
      case "PUT":
        httpMethod = new PutMethod(uri.toString())
        break
      case "POST":
        httpMethod = new PostMethod(uri.toString())
        break
      case "HEAD":
        httpMethod = new HeadMethod(uri.toString())
        break
      case "DELETE":
        httpMethod = new DeleteMethod(uri.toString())
        break
      case "OPTIONS":
        httpMethod = new OptionsMethod(uri.toString())
        break
      case "TRACE":
        httpMethod = new TraceMethod(uri.toString())
        break
      default:
        throw new RuntimeException("Unsupported method: " + method)
    }

    headers.each { httpMethod.setRequestHeader(it.key, it.value) }

    try {
      client.executeMethod(httpMethod)
      callback?.call()
      return httpMethod.getStatusCode()
    } finally {
      httpMethod.releaseConnection()
    }
  }

  @Override
  boolean testRedirects() {
    // Generates 4 spans
    false
  }
}
