/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.base.HttpClientTest
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import org.apache.commons.httpclient.HttpClient
import org.apache.commons.httpclient.HttpMethod
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager
import org.apache.commons.httpclient.methods.DeleteMethod
import org.apache.commons.httpclient.methods.GetMethod
import org.apache.commons.httpclient.methods.HeadMethod
import org.apache.commons.httpclient.methods.OptionsMethod
import org.apache.commons.httpclient.methods.PostMethod
import org.apache.commons.httpclient.methods.PutMethod
import org.apache.commons.httpclient.methods.TraceMethod
import spock.lang.Shared

class CommonsHttpClientTest extends HttpClientTest<HttpMethod> implements AgentTestTrait {
  @Shared
  HttpClient client = new HttpClient(new MultiThreadedHttpConnectionManager())

  def setupSpec() {
    client.setConnectionTimeout(CONNECT_TIMEOUT_MS)
  }

  @Override
  HttpMethod buildRequest(String method, URI uri, Map<String, String> headers) {
    def request
    switch (method) {
      case "GET":
        request = new GetMethod(uri.toString())
        break
      case "PUT":
        request = new PutMethod(uri.toString())
        break
      case "POST":
        request = new PostMethod(uri.toString())
        break
      case "HEAD":
        request = new HeadMethod(uri.toString())
        break
      case "DELETE":
        request = new DeleteMethod(uri.toString())
        break
      case "OPTIONS":
        request = new OptionsMethod(uri.toString())
        break
      case "TRACE":
        request = new TraceMethod(uri.toString())
        break
      default:
        throw new IllegalStateException("Unsupported method: " + method)
    }
    headers.each { request.setRequestHeader(it.key, it.value) }
    return request
  }

  @Override
  int sendRequest(HttpMethod request, String method, URI uri, Map<String, String> headers) {
    boolean useTimeout = uri.toString().contains("/read-timeout")
    if (useTimeout) {
      client.setTimeout(READ_TIMEOUT_MS)
    }
    try {
      client.executeMethod(request)
      return request.getStatusCode()
    } finally {
      if (useTimeout) {
        client.setTimeout(0)
      }
      request.releaseConnection()
    }
  }

  @Override
  boolean testCircularRedirects() {
    false
  }

  @Override
  boolean testReusedRequest() {
    // apache commons throws an exception if the request is reused without being recycled first
    // at which point this test is not useful (and requires re-populating uri)
    false
  }

  @Override
  boolean testCallback() {
    false
  }

  @Override
  boolean testReadTimeout() {
    true
  }

  @Override
  Set<AttributeKey<?>> httpAttributes(URI uri) {
    Set<AttributeKey<?>> extra = [
      SemanticAttributes.HTTP_SCHEME,
      SemanticAttributes.HTTP_TARGET
    ]
    super.httpAttributes(uri) + extra
  }
}
