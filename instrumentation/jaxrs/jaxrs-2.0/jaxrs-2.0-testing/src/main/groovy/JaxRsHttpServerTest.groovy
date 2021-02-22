/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static io.opentelemetry.api.trace.SpanKind.INTERNAL
import static io.opentelemetry.api.trace.SpanKind.SERVER
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.EXCEPTION
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.PATH_PARAM
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.SUCCESS
import static java.util.concurrent.TimeUnit.SECONDS
import static org.junit.Assume.assumeTrue

import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.asserts.TraceAssert
import io.opentelemetry.instrumentation.test.base.HttpServerTest
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import java.util.concurrent.CompletableFuture
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.Response
import spock.lang.Timeout
import spock.lang.Unroll

abstract class JaxRsHttpServerTest<S> extends HttpServerTest<S> implements AgentTestTrait {
  @Timeout(10)
  @Unroll
  def "should handle #desc AsyncResponse"() {
    given:
    def url = HttpUrl.get(address.resolve("async")).newBuilder()
      .addQueryParameter("action", action)
      .build()
    def request = request(url, "GET", null).build()

    when: "async call is started"
    def futureResponse = asyncCall(request)

    then: "there are no traces yet"
    assertTraces(0) {
    }

    when: "barrier is released and resource class sends response"
    JaxRsTestResource.BARRIER.await(1, SECONDS)
    def response = futureResponse.join()

    then:
    assert response.code() == statusCode
    assert bodyPredicate(response.body().string())

    def spanCount = 2
    def hasSendError = asyncCancelHasSendError() && action == "cancel"
    if (hasSendError) {
      spanCount++
    }
    assertTraces(1) {
      trace(0, spanCount) {
        asyncServerSpan(it, 0, url, statusCode)
        handlerSpan(it, 1, span(0), "asyncOp", isCancelled, isError, errorMessage)
        if (hasSendError) {
          sendErrorSpan(it, 2, span(1))
        }
      }
    }

    where:
    desc         | action    | statusCode | bodyPredicate            | isCancelled | isError | errorMessage
    "successful" | "succeed" | 200        | { it == "success" }      | false       | false   | null
    "failing"    | "throw"   | 500        | { it == "failure" }      | false       | true    | "failure"
    "canceled"   | "cancel"  | 503        | { it instanceof String } | true        | false   | null
  }

  @Timeout(10)
  @Unroll
  def "should handle #desc CompletionStage (JAX-RS 2.1+ only)"() {
    assumeTrue(shouldTestCompletableStageAsync())

    given:
    def url = HttpUrl.get(address.resolve("async-completion-stage")).newBuilder()
      .addQueryParameter("action", action)
      .build()
    def request = request(url, "GET", null).build()

    when: "async call is started"
    def futureResponse = asyncCall(request)

    then: "there are no traces yet"
    assertTraces(0) {
    }

    when: "barrier is released and resource class sends response"
    JaxRsTestResource.BARRIER.await(1, SECONDS)
    def response = futureResponse.join()

    then:
    assert response.code() == statusCode
    assert bodyPredicate(response.body().string())

    assertTraces(1) {
      trace(0, 2) {
        asyncServerSpan(it, 0, url, statusCode)
        handlerSpan(it, 1, span(0), "jaxRs21Async", false, isError, errorMessage)
      }
    }

    where:
    desc         | action    | statusCode | bodyPredicate       | isError | errorMessage
    "successful" | "succeed" | 200        | { it == "success" } | false   | null
    "failing"    | "throw"   | 500        | { it == "failure" } | true    | "failure"
  }

  @Override
  boolean hasHandlerSpan() {
    true
  }

  @Override
  boolean testNotFound() {
    false
  }

  @Override
  boolean testPathParam() {
    true
  }

  boolean asyncCancelHasSendError() {
    false
  }

  private static boolean shouldTestCompletableStageAsync() {
    Boolean.getBoolean("testLatestDeps")
  }

  @Override
  void serverSpan(TraceAssert trace,
                  int index,
                  String traceID = null,
                  String parentID = null,
                  String method = "GET",
                  Long responseContentLength = null,
                  ServerEndpoint endpoint = SUCCESS) {
    serverSpan(trace, index, traceID, parentID, method,
      endpoint == PATH_PARAM ? getContextPath() + "/path/{id}/param" : endpoint.resolvePath(address).path,
      endpoint.resolve(address),
      endpoint.errored,
      endpoint.status,
      endpoint.query)
  }

  void asyncServerSpan(TraceAssert trace,
                       int index,
                       HttpUrl url,
                       int statusCode) {
    def rawUrl = url.url()
    serverSpan(trace, index, null, null, "GET",
      rawUrl.path,
      rawUrl.toURI(),
      statusCode >= 500,
      statusCode,
      null)
  }

  void serverSpan(TraceAssert trace,
                  int index,
                  String traceID,
                  String parentID,
                  String method,
                  String path,
                  URI fullUrl,
                  boolean isError,
                  int statusCode,
                  String query) {
    trace.span(index) {
      name path
      kind SERVER
      errored isError
      if (parentID != null) {
        traceId traceID
        parentSpanId parentID
      } else {
        hasNoParent()
      }
      attributes {
        "${SemanticAttributes.NET_PEER_IP.key}" { it == null || it == "127.0.0.1" } // Optional
        "${SemanticAttributes.NET_PEER_PORT.key}" Long
        "${SemanticAttributes.HTTP_URL.key}" fullUrl.toString()
        "${SemanticAttributes.HTTP_METHOD.key}" method
        "${SemanticAttributes.HTTP_STATUS_CODE.key}" statusCode
        "${SemanticAttributes.HTTP_FLAVOR.key}" "1.1"
        "${SemanticAttributes.HTTP_USER_AGENT.key}" TEST_USER_AGENT
        "${SemanticAttributes.HTTP_CLIENT_IP.key}" TEST_CLIENT_IP
      }
    }
  }

  @Override
  void handlerSpan(TraceAssert trace,
                   int index,
                   Object parent,
                   String method = "GET",
                   ServerEndpoint endpoint = SUCCESS) {
    handlerSpan(trace, index, parent,
      endpoint.name().toLowerCase(),
      false,
      endpoint == EXCEPTION,
      EXCEPTION.body)
  }

  void handlerSpan(TraceAssert trace,
                   int index,
                   Object parent,
                   String methodName,
                   boolean isCancelled,
                   boolean isError,
                   String exceptionMessage = null) {
    trace.span(index) {
      name "JaxRsTestResource.${methodName}"
      kind INTERNAL
      errored isError
      if (isError) {
        errorEvent(Exception, exceptionMessage)
      }
      childOf((SpanData) parent)
      attributes {
        if (isCancelled) {
          "jaxrs.canceled" true
        }
      }
    }
  }

  private CompletableFuture<Response> asyncCall(Request request) {
    def future = new CompletableFuture()

    client.newCall(request).enqueue(new Callback() {
      @Override
      void onFailure(Call call, IOException e) {
        future.completeExceptionally(e)
      }

      @Override
      void onResponse(Call call, Response response) throws IOException {
        future.complete(response)
      }
    })

    return future
  }
}
