/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static io.opentelemetry.instrumentation.test.utils.PortUtils.UNUSABLE_PORT
import static io.opentelemetry.instrumentation.test.utils.TraceUtils.basicSpan
import static io.opentelemetry.instrumentation.test.utils.TraceUtils.runUnderTrace

import com.ning.http.client.AsyncCompletionHandler
import com.ning.http.client.AsyncHttpClient
import com.ning.http.client.AsyncHttpClientConfig
import com.ning.http.client.Response
import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.base.HttpClientTest
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import spock.lang.AutoCleanup
import spock.lang.Shared

class Netty38ClientTest extends HttpClientTest implements AgentTestTrait {

  @Shared
  def clientConfig = new AsyncHttpClientConfig.Builder()
    .setRequestTimeout(TimeUnit.SECONDS.toMillis(10).toInteger())
    .build()

  @Shared
  @AutoCleanup
  AsyncHttpClient asyncHttpClient = new AsyncHttpClient(clientConfig)

  @Override
  int doRequest(String method, URI uri, Map<String, String> headers, Closure callback) {
    def methodName = "prepare" + method.toLowerCase().capitalize()
    def requestBuilder = asyncHttpClient."$methodName"(uri.toString())
    headers.each { requestBuilder.setHeader(it.key, it.value) }
    def response = requestBuilder.execute(new AsyncCompletionHandler() {
      @Override
      Object onCompleted(Response response) throws Exception {
        callback?.call()
        return response
      }
    }).get()
    return response.statusCode
  }

  @Override
  String userAgent() {
    return "AHC"
  }

  @Override
  boolean testRedirects() {
    false
  }

  @Override
  boolean testConnectionFailure() {
    false
  }

  @Override
  boolean testRemoteConnection() {
    return false
  }

  def "connection error (unopened port)"() {
    given:
    def uri = new URI("http://127.0.0.1:$UNUSABLE_PORT/")

    when:
    runUnderTrace("parent") {
      doRequest(method, uri)
    }

    then:
    def ex = thrown(Exception)
    def thrownException = ex instanceof ExecutionException ? ex.cause : ex

    and:
    assertTraces(1) {
      trace(0, 2) {
        basicSpan(it, 0, "parent", null, thrownException)

        span(1) {
          name "CONNECT"
          childOf span(0)
          errored true
          Class errorClass = ConnectException
          try {
            errorClass = Class.forName('io.netty.channel.AbstractChannel$AnnotatedConnectException')
          } catch (ClassNotFoundException e) {
            // Older versions use 'java.net.ConnectException' and do not have 'io.netty.channel.AbstractChannel$AnnotatedConnectException'
          }
          errorEvent(errorClass, ~/Connection refused:( no further information:)? \/127.0.0.1:$UNUSABLE_PORT/)
        }
      }
    }

    where:
    method = "GET"
  }
}
