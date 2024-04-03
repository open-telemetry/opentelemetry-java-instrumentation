/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import com.sun.jersey.api.client.Client
import com.sun.jersey.api.client.ClientHandlerException
import com.sun.jersey.api.client.ClientResponse
import com.sun.jersey.api.client.WebResource
import com.sun.jersey.api.client.filter.GZIPContentEncodingFilter
import com.sun.jersey.api.client.filter.LoggingFilter
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.base.HttpClientTest
import io.opentelemetry.semconv.NetworkAttributes
import spock.lang.Shared

class JaxRsClientV1Test extends HttpClientTest<WebResource.Builder> implements AgentTestTrait {

  @Shared
  Client client = buildClient(false)

  @Shared
  Client clientWithReadTimeout = buildClient(true)

  def buildClient(boolean readTimeout) {
    def client = Client.create()
    client.setConnectTimeout(CONNECT_TIMEOUT_MS)
    if (readTimeout) {
      client.setReadTimeout(READ_TIMEOUT_MS)
    }
    // Add filters to ensure spans aren't duplicated.
    client.addFilter(new LoggingFilter())
    client.addFilter(new GZIPContentEncodingFilter())

    return client
  }

  Client getClient(URI uri) {
    if (uri.toString().contains("/read-timeout")) {
      return clientWithReadTimeout
    }
    return client
  }

  @Override
  def cleanupSpec() {
    client?.destroy()
    clientWithReadTimeout?.destroy()
  }

  @Override
  WebResource.Builder buildRequest(String method, URI uri, Map<String, String> headers) {
    def resource = getClient(uri).resource(uri).requestBuilder
    headers.each { resource.header(it.key, it.value) }
    return resource
  }

  @Override
  int sendRequest(WebResource.Builder resource, String method, URI uri, Map<String, String> headers) {
    def body = BODY_METHODS.contains(method) ? "" : null
    try {
      return resource.method(method, ClientResponse, body).status
    } catch (ClientHandlerException exception) {
      throw exception.getCause()
    }
  }

  @Override
  boolean testCircularRedirects() {
    false
  }

  @Override
  boolean testCallback() {
    false
  }

  @Override
  Set<AttributeKey<?>> httpAttributes(URI uri) {
    def attributes = super.httpAttributes(uri)
    attributes.remove(NetworkAttributes.NETWORK_PROTOCOL_VERSION)
    return attributes
  }

  @Override
  boolean testNonStandardHttpMethod() {
    false
  }
}
