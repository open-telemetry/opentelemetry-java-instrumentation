/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jetty.httpclient.v9_2

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.instrumentation.test.base.HttpClientTest
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import org.eclipse.jetty.client.HttpClient
import org.eclipse.jetty.client.api.ContentResponse
import org.eclipse.jetty.client.api.Request
import org.eclipse.jetty.client.api.Response
import org.eclipse.jetty.client.api.Result
import org.eclipse.jetty.http.HttpMethod
import org.eclipse.jetty.util.ssl.SslContextFactory
import spock.lang.Shared
import spock.lang.Unroll

import java.util.concurrent.TimeUnit

abstract class AbstractJettyClient9Test extends HttpClientTest<Request> {

  abstract HttpClient createStandardClient()

  abstract HttpClient createHttpsClient(SslContextFactory sslContextFactory)


  @Shared
  def client = createStandardClient()
  @Shared
  def httpsClient = null

  Request jettyRequest = null

  def setupSpec() {
    //Start the main Jetty HttpClient and a https client
    client.start()

    SslContextFactory tlsCtx = new SslContextFactory()
    httpsClient = createHttpsClient(tlsCtx)
    httpsClient.setFollowRedirects(false)
    httpsClient.start()
  }

  @Override
  Request buildRequest(String method, URI uri, Map<String, String> headers) {
    HttpClient theClient = uri.scheme == 'https' ? httpsClient : client

    Request request = theClient.newRequest(uri)
    request.agent("Jetty")

    HttpMethod methodObj = HttpMethod.valueOf(method)
    request.method(methodObj)
    request.timeout(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)

    jettyRequest = request

    return request
  }

  @Override
  String userAgent() {
    return "Jetty"
  }

  @Override
  int sendRequest(Request request, String method, URI uri, Map<String, String> headers) {
    headers.each { k, v ->
      request.header(k, v)
    }

    ContentResponse response = request.send()

    return response.status
  }

  private static class JettyClientListener implements Request.FailureListener, Response.FailureListener {
    volatile Throwable failure

    @Override
    void onFailure(Request requestF, Throwable failure) {
      this.failure = failure
    }

    @Override
    void onFailure(Response responseF, Throwable failure) {
      this.failure = failure
    }
  }

  @Override
  void sendRequestWithCallback(Request request, String method, URI uri, Map<String, String> headers, AbstractHttpClientTest.RequestResult requestResult) {
    JettyClientListener jcl = new JettyClientListener()

    request.onRequestFailure(jcl)
    request.onResponseFailure(jcl)
    headers.each { k, v ->
      request.header(k, v)
    }

    request.send(new Response.CompleteListener() {
      @Override
      void onComplete(Result result) {
        if (jcl.failure != null) {
          requestResult.complete(jcl.failure)
          return
        }

        requestResult.complete(result.response.status)
      }
    })
  }

  @Override
  boolean testRedirects() {
    false
  }

  @Override
  Set<AttributeKey<?>> httpAttributes(URI uri) {
    Set<AttributeKey<?>> extra = [
      SemanticAttributes.HTTP_SCHEME,
      SemanticAttributes.HTTP_TARGET,
      SemanticAttributes.HTTP_HOST
    ]
    super.httpAttributes(uri) + extra
  }

  @Unroll
  def "test content of #method request #url"() {
    when:
    def request = buildRequest(method, url, [:])
    def response = request.send()

    then:
    response.status == 200
    response.getContentAsString() == "Hello."

    assertTraces(1) {
      trace(0, 2) {
        clientSpan(it, 0, null, method, url)
        serverSpan(it, 1, span(0))
      }
    }

    where:
    path << ["/success"]

    method = "GET"
    url = resolveAddress(path)
  }
}
