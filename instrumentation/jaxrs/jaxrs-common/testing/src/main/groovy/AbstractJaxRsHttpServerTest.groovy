/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.asserts.TraceAssert
import io.opentelemetry.instrumentation.test.base.HttpServerTest
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.semconv.SemanticAttributes
import spock.lang.Unroll

import java.util.concurrent.TimeUnit

import static io.opentelemetry.api.trace.SpanKind.INTERNAL
import static io.opentelemetry.api.trace.SpanKind.SERVER
import static io.opentelemetry.api.trace.StatusCode.ERROR
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.EXCEPTION
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.PATH_PARAM
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.SUCCESS
import static java.util.concurrent.TimeUnit.SECONDS
import static org.junit.jupiter.api.Assumptions.assumeTrue

abstract class AbstractJaxRsHttpServerTest<S> extends HttpServerTest<S> implements AgentTestTrait {

  abstract void awaitBarrier(int amount, TimeUnit timeUnit)

  def "test super method without @Path"() {
    given:
    def response = client.get(address.resolve("test-resource-super").toString()).aggregate().join()

    expect:
    response.status().code() == SUCCESS.status
    response.contentUtf8() == SUCCESS.body

    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          hasNoParent()
          kind SERVER
          name "GET " + getContextPath() + "/test-resource-super"
        }
        span(1) {
          name "controller"
          kind INTERNAL
          childOf span(0)
        }
      }
    }
  }

  def "test interface method with @Path"() {
    assumeTrue(testInterfaceMethodWithPath())

    given:
    def response = client.get(address.resolve("test-resource-interface/call").toString()).aggregate().join()

    expect:
    response.status().code() == SUCCESS.status
    response.contentUtf8() == SUCCESS.body

    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          hasNoParent()
          kind SERVER
          name "GET " + getContextPath() + "/test-resource-interface/call"
        }
        span(1) {
          name "controller"
          kind INTERNAL
          childOf span(0)
        }
      }
    }
  }

  def "test sub resource locator"() {
    given:
    def response = client.get(address.resolve("test-sub-resource-locator/call/sub").toString()).aggregate().join()

    expect:
    response.status().code() == SUCCESS.status
    response.contentUtf8() == SUCCESS.body

    assertTraces(1) {
      trace(0, 5) {
        span(0) {
          hasNoParent()
          kind SERVER
          name "GET " + getContextPath() + "/test-sub-resource-locator/call/sub"
        }
        span(1) {
          name "JaxRsSubResourceLocatorTestResource.call"
          kind INTERNAL
          childOf span(0)
        }
        span(2) {
          name "controller"
          kind INTERNAL
          childOf span(1)
        }
        span(3) {
          name "SubResource.call"
          kind INTERNAL
          childOf span(0)
        }
        span(4) {
          name "controller"
          kind INTERNAL
          childOf span(3)
        }
      }
    }
  }

  @Unroll
  def "should handle #desc AsyncResponse"() {
    given:
    def url = address.resolve("async?action=${action}").toString()

    when: "async call is started"
    def futureResponse = client.get(url).aggregate()

    then: "there are no traces yet"
    assertTraces(0) {
    }

    when: "barrier is released and resource class sends response"
    awaitBarrier(1, SECONDS)
    def response = futureResponse.join()

    then:
    response.status().code() == statusCode
    bodyPredicate(response.contentUtf8())

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

  @Unroll
  def "should handle #desc CompletionStage (JAX-RS 2.1+ only)"() {
    assumeTrue(shouldTestCompletableStageAsync())
    given:
    def url = address.resolve("async-completion-stage?action=${action}").toString()

    when: "async call is started"
    def futureResponse = client.get(url).aggregate()

    then: "there are no traces yet"
    assertTraces(0) {
    }

    when: "barrier is released and resource class sends response"
    awaitBarrier(1, SECONDS)
    def response = futureResponse.join()

    then:
    response.status().code() == statusCode
    bodyPredicate(response.contentUtf8())

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
  boolean hasHandlerSpan(ServerEndpoint endpoint) {
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

  boolean testInterfaceMethodWithPath() {
    true
  }

  boolean asyncCancelHasSendError() {
    false
  }

  boolean shouldTestCompletableStageAsync() {
    Boolean.getBoolean("testLatestDeps")
  }

  @Override
  boolean hasResponseCustomizer(ServerEndpoint endpoint) {
    true
  }

  @Override
  void serverSpan(TraceAssert trace,
                  int index,
                  String traceID = null,
                  String parentID = null,
                  String method = "GET",
                  ServerEndpoint endpoint = SUCCESS,
                  String spanID = null) {
    serverSpan(trace, index, traceID, parentID, spanID, method,
      endpoint == PATH_PARAM ? getContextPath() + "/path/{id}/param" : endpoint.resolvePath(address).path,
      endpoint.resolve(address),
      endpoint.status,
      endpoint.query)
  }

  void asyncServerSpan(TraceAssert trace,
                       int index,
                       String url,
                       int statusCode) {
    def rawUrl = URI.create(url).toURL()
    serverSpan(trace, index, null, null, null, "GET",
      rawUrl.path,
      rawUrl.toURI(),
      statusCode,
      null)
  }

  void serverSpan(TraceAssert trace,
                  int index,
                  String traceID,
                  String parentID,
                  String spanID,
                  String method,
                  String path,
                  URI fullUrl,
                  int statusCode,
                  String query) {
    trace.span(index) {
      name method + " " + path
      kind SERVER
      if (statusCode >= 500) {
        status ERROR
      }
      if (traceID != null) {
        traceId traceID
      }
      if (parentID != null) {
        parentSpanId parentID
      } else {
        hasNoParent()
      }
      if (spanID != null) {
        spanId spanID
      }
      attributes {
        "$SemanticAttributes.NET_PROTOCOL_NAME" "http"
        "$SemanticAttributes.NET_PROTOCOL_VERSION" "1.1"
        "$SemanticAttributes.NET_HOST_NAME" fullUrl.host
        "$SemanticAttributes.NET_HOST_PORT" fullUrl.port
        "$SemanticAttributes.NET_SOCK_PEER_ADDR" "127.0.0.1"
        "$SemanticAttributes.NET_SOCK_PEER_PORT" Long
        "$SemanticAttributes.NET_SOCK_HOST_ADDR" "127.0.0.1"
        "$SemanticAttributes.HTTP_SCHEME" fullUrl.getScheme()
        "$SemanticAttributes.HTTP_TARGET" fullUrl.getPath() + (fullUrl.getQuery() != null ? "?" + fullUrl.getQuery() : "")
        "$SemanticAttributes.HTTP_METHOD" method
        "$SemanticAttributes.HTTP_STATUS_CODE" statusCode
        "$SemanticAttributes.USER_AGENT_ORIGINAL" TEST_USER_AGENT
        "$SemanticAttributes.HTTP_CLIENT_IP" TEST_CLIENT_IP
        // Optional
        "$SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH" { it == null || it instanceof Long }
        "$SemanticAttributes.HTTP_ROUTE" path
        if (fullUrl.getPath().endsWith(ServerEndpoint.CAPTURE_HEADERS.getPath())) {
          "http.request.header.x_test_request" { it == ["test"] }
          "http.response.header.x_test_response" { it == ["test"] }
        }
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
      if (isError) {
        status ERROR
        errorEvent(Exception, exceptionMessage)
      }
      childOf((SpanData) parent)
      attributes {
        "$SemanticAttributes.CODE_NAMESPACE" "test.JaxRsTestResource"
        "$SemanticAttributes.CODE_FUNCTION" methodName
        if (isCancelled) {
          "jaxrs.canceled" true
        }
      }
    }
  }
}
