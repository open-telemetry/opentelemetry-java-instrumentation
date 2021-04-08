/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import com.sun.jersey.api.client.Client
import com.sun.jersey.api.client.ClientResponse
import com.sun.jersey.api.client.WebResource
import com.sun.jersey.api.client.filter.GZIPContentEncodingFilter
import com.sun.jersey.api.client.filter.LoggingFilter
import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.base.HttpClientTest
import spock.lang.Shared

class JaxRsClientV1Test extends HttpClientTest implements AgentTestTrait {

  @Shared
  Client client = Client.create()

  def setupSpec() {
    client.setConnectTimeout(CONNECT_TIMEOUT_MS)
    // Add filters to ensure spans aren't duplicated.
    client.addFilter(new LoggingFilter())
    client.addFilter(new GZIPContentEncodingFilter())
  }

  @Override
  int doRequest(String method, URI uri, Map<String, String> headers = [:]) {
    def request = buildRequest(uri, headers)
    return sendRequest(request, method)
  }

  @Override
  int doReusedRequest(String method, URI uri) {
    def request = buildRequest(uri, [:])
    sendRequest(request, method)
    return sendRequest(request, method)
  }

  private WebResource.Builder buildRequest(URI uri, Map<String, String> headers) {
    def resource = client.resource(uri).requestBuilder
    headers.each { resource.header(it.key, it.value) }
    return resource
  }

  private int sendRequest(WebResource.Builder resource, String method) {
    def body = BODY_METHODS.contains(method) ? "" : null
    return resource.method(method, ClientResponse, body).status
  }

  @Override
  boolean testCircularRedirects() {
    false
  }

  @Override
  boolean testCallback() {
    false
  }
}
