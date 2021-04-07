/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static io.opentelemetry.api.trace.SpanKind.CLIENT
import static io.opentelemetry.instrumentation.test.utils.PortUtils.UNUSABLE_PORT
import static io.opentelemetry.instrumentation.test.utils.TraceUtils.basicSpan
import static io.opentelemetry.instrumentation.test.utils.TraceUtils.runUnderTrace

import com.ning.http.client.AsyncCompletionHandler
import com.ning.http.client.AsyncHttpClient
import com.ning.http.client.AsyncHttpClientConfig
import com.ning.http.client.Request
import com.ning.http.client.RequestBuilder
import com.ning.http.client.Response
import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.base.HttpClientTest
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import spock.lang.AutoCleanup
import spock.lang.Shared

class Netty38ClientTest extends HttpClientTest implements AgentTestTrait {

  @Shared
  def clientConfig = new AsyncHttpClientConfig.Builder()
    .setRequestTimeoutInMs(TimeUnit.SECONDS.toMillis(10).toInteger())
    .build()

  @Shared
  @AutoCleanup
  AsyncHttpClient client = new AsyncHttpClient(clientConfig)

  @Override
  int doRequest(String method, URI uri, Map<String, String> headers = [:]) {
    return client.executeRequest(buildRequest(method, uri, headers)).get().statusCode
  }

  @Override
  void doRequestWithCallback(String method, URI uri, Map<String, String> headers = [:], Consumer<Integer> callback) {
    // TODO(anuraaga): Do we also need to test ListenableFuture callback?
    client.executeRequest(buildRequest(method, uri, headers), new AsyncCompletionHandler<Void>() {
      @Override
      Void onCompleted(Response response) throws Exception {
        callback.accept(response.statusCode)
        return null
      }
    })
  }

  private static Request buildRequest(String method, URI uri, Map<String, String> headers) {
    RequestBuilder requestBuilder = new RequestBuilder(method)
      .setUrl(uri.toString())
    headers.entrySet().each {
      requestBuilder.addHeader(it.key, it.value)
    }
    return requestBuilder.build()
  }

  @Override
  String userAgent() {
    return "NING"
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

  // This is almost identical to "connection error (unopened port)" test from superclass.
  // But it uses somewhat different span name for the client span.
  // For now creating a separate test for this, hoping to remove this duplication in the future.
  def "netty connection error (unopened port)"() {
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
          kind CLIENT
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
