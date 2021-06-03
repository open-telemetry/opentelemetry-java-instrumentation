/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jetty.httpclient.v9_0

import io.opentelemetry.context.Context
import io.opentelemetry.context.Scope
import io.opentelemetry.instrumentation.test.base.HttpClientTest
import org.eclipse.jetty.client.HttpClient
import org.eclipse.jetty.client.api.ContentResponse
import org.eclipse.jetty.client.api.Request
import org.eclipse.jetty.client.api.Response
import org.eclipse.jetty.client.api.Result
import org.eclipse.jetty.http.HttpMethod
import org.eclipse.jetty.util.ssl.SslContextFactory
import org.junit.jupiter.api.Assertions
import spock.lang.Shared

import java.util.concurrent.TimeUnit

abstract class AbstractJettyClient9Test extends HttpClientTest<Request> {


//  abstract void attachInterceptor(Request jettyRequest, Context parentContext);
  abstract HttpClient createStandardClient();

  abstract HttpClient createHttpsClient(SslContextFactory sslContextFactory);


  @Shared
  def client = createStandardClient()
  @Shared
  def httpsClient = null

  def setupSpec() {

    try {
      client.start()
    } catch (Throwable t) {
      Assertions.fail("Error during jetty client start", t)
    }

    SslContextFactory tlsCtx = new SslContextFactory()
    tlsCtx.setExcludeProtocols("TLSv1.3")
    httpsClient = createHttpsClient(tlsCtx)
    httpsClient.setFollowRedirects(false)
    try {
      httpsClient.start()
    } catch (Throwable t) {
      Assertions.fail("Error during jetty client https start", t)
    }

  }

  @Override
  Request buildRequest(String method, URI uri, Map<String, String> headers) {

    HttpClient theClient = uri.scheme == 'https' ? httpsClient : client

    Request request = theClient.newRequest(uri)

    HttpMethod methodObj = HttpMethod.valueOf(method)
    request.method(methodObj)
    request.timeout(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)

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

//    def interceptor = createInterceptor(Context.current())
    Context parentContext = Context.current()
    Scope scope = parentContext.makeCurrent()
//    attachInterceptor(request, parentContext)
    ContentResponse response = request.send()
    scope.close()

    return response.status
  }

  private static class JettyClientListener implements Request.FailureListener, Response.FailureListener {

    Throwable failure

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
  void sendRequestWithCallback(Request request, String method, URI uri, Map<String, String> headers, RequestResult requestResult) {

    JettyClientListener jcl = new JettyClientListener()

    request.onRequestFailure(jcl)
    request.onResponseFailure(jcl)
    headers.each { k, v ->
      request.header(k, v)
    }
    Context parentContext = Context.current()
    Scope scope = parentContext.makeCurrent()
//    attachInterceptor(request, parentContext)

    request.send(new Response.CompleteListener() {
      @Override
      void onComplete(Result result) {
        if (jcl.failure != null) {
          requestResult.complete(jcl.failure)
          return;
        }

        requestResult.complete(result.response.status)
      }
    })
    scope.close()
  }


  @Override
  boolean testRedirects() {
    false
  }

  @Override
  boolean testCausality() {
    true
  }

}
