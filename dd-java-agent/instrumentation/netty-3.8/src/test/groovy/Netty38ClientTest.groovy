import com.ning.http.client.AsyncCompletionHandler
import com.ning.http.client.AsyncHttpClient
import com.ning.http.client.AsyncHttpClientConfig
import com.ning.http.client.Response
import datadog.trace.agent.test.base.HttpClientTest
import datadog.trace.bootstrap.instrumentation.api.Tags
import datadog.trace.instrumentation.netty38.client.NettyHttpClientDecorator
import spock.lang.AutoCleanup
import spock.lang.Shared

import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

import static datadog.trace.agent.test.utils.PortUtils.UNUSABLE_PORT
import static datadog.trace.agent.test.utils.TraceUtils.basicSpan
import static datadog.trace.agent.test.utils.TraceUtils.runUnderTrace

class Netty38ClientTest extends HttpClientTest {

  @Shared
  def clientConfig = new AsyncHttpClientConfig.Builder().setRequestTimeoutInMs(TimeUnit.SECONDS.toMillis(10).toInteger()).build()
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
    blockUntilChildSpansFinished(1)
    return response.statusCode
  }

  @Override
  String component() {
    return NettyHttpClientDecorator.DECORATE.component()
  }

  @Override
  String expectedOperationName() {
    return "netty.client.request"
  }

  @Override
  boolean testRedirects() {
    false
  }

  @Override
  boolean testConnectionFailure() {
    false
  }

  def "connection error (unopened port)"() {
    given:
    def uri = new URI("http://localhost:$UNUSABLE_PORT/")

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
          operationName "netty.connect"
          resourceName "netty.connect"
          childOf span(0)
          errored true
          tags {
            "$Tags.COMPONENT" "netty"
            Class errorClass = ConnectException
            try {
              errorClass = Class.forName('io.netty.channel.AbstractChannel$AnnotatedConnectException')
            } catch (ClassNotFoundException e) {
              // Older versions use 'java.net.ConnectException' and do not have 'io.netty.channel.AbstractChannel$AnnotatedConnectException'
            }
            errorTags errorClass, "Connection refused: localhost/127.0.0.1:$UNUSABLE_PORT"
            defaultTags()
          }
        }
      }
    }

    where:
    method = "GET"
  }
}
