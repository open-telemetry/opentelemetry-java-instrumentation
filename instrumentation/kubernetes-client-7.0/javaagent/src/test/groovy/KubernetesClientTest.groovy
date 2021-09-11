/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.kubernetes.client.openapi.ApiCallback
import io.kubernetes.client.openapi.ApiClient
import io.kubernetes.client.openapi.ApiException
import io.kubernetes.client.openapi.apis.CoreV1Api
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.instrumentation.test.asserts.TraceAssert
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import io.opentelemetry.testing.internal.armeria.common.HttpResponse
import io.opentelemetry.testing.internal.armeria.common.HttpStatus
import io.opentelemetry.testing.internal.armeria.common.MediaType
import io.opentelemetry.testing.internal.armeria.testing.junit5.server.mock.MockWebServerExtension
import spock.lang.Shared

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference

import static io.opentelemetry.api.trace.SpanKind.CLIENT
import static io.opentelemetry.api.trace.StatusCode.ERROR
import static io.opentelemetry.instrumentation.test.utils.TraceUtils.runInternalSpan
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.NetTransportValues.IP_TCP

class KubernetesClientTest extends AgentInstrumentationSpecification {
  private static final String TEST_USER_AGENT = "test-user-agent"

  @Shared
  def server = new MockWebServerExtension()

  @Shared
  CoreV1Api api

  def setupSpec() {
    server.start()
    def apiClient = new ApiClient()
    apiClient.setUserAgent(TEST_USER_AGENT)
    apiClient.basePath = server.httpUri().toString()
    api = new CoreV1Api(apiClient)
  }

  def cleanupSpec() {
    server.stop()
  }

  def setup() {
    server.beforeTestExecution(null)
  }

  def "Kubernetes span is registered on a synchronous call"() {
    given:
    server.enqueue(HttpResponse.of(HttpStatus.OK, MediaType.PLAIN_TEXT_UTF_8, "42"))

    when:
    def response = runWithSpan("parent") {
      api.connectGetNamespacedPodProxy("name", "namespace", "path")
    }

    then:
    response == "42"
    server.takeRequest().request().headers().get("traceparent") != null

    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          name "parent"
          kind SpanKind.INTERNAL
          hasNoParent()
        }
        apiClientSpan(it, 1, "get  pods/proxy", "${server.httpUri()}/api/v1/namespaces/namespace/pods/name/proxy?path=path", 200)
      }
    }
  }

  def "Kubernetes instrumentation handles errors on a synchronous call"() {
    given:
    server.enqueue(HttpResponse.of(HttpStatus.valueOf(451), MediaType.PLAIN_TEXT_UTF_8, "42"))

    when:
    runWithSpan("parent") {
      api.connectGetNamespacedPodProxy("name", "namespace", "path")
    }

    then:
    def exception = thrown(ApiException)
    server.takeRequest().request().headers().get("traceparent") != null

    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          name "parent"
          kind SpanKind.INTERNAL
          hasNoParent()
          status ERROR
          errorEvent(exception.class, exception.message)
        }
        apiClientSpan(it, 1, "get  pods/proxy", "${server.httpUri()}/api/v1/namespaces/namespace/pods/name/proxy?path=path", 451, exception)
      }
    }
  }

  def "Kubernetes span is registered on an asynchronous call"() {
    given:
    server.enqueue(HttpResponse.of(HttpStatus.OK, MediaType.PLAIN_TEXT_UTF_8, "42"))

    when:
    def responseBody = new AtomicReference<String>()
    def latch = new CountDownLatch(1)

    runWithSpan("parent") {
      api.connectGetNamespacedPodProxyAsync("name", "namespace", "path", new ApiCallbackTemplate() {
        @Override
        void onSuccess(String result, int statusCode, Map<String, List<String>> responseHeaders) {
          responseBody.set(result)
          latch.countDown()
          runInternalSpan("callback")
        }
      })
    }

    then:
    latch.await()
    responseBody.get() == "42"
    server.takeRequest().request().headers().get("traceparent") != null

    assertTraces(1) {
      trace(0, 3) {
        span(0) {
          name "parent"
          kind SpanKind.INTERNAL
          hasNoParent()
        }
        apiClientSpan(it, 1, "get  pods/proxy", "${server.httpUri()}/api/v1/namespaces/namespace/pods/name/proxy?path=path", 200)
        span(2) {
          name "callback"
          kind SpanKind.INTERNAL
          childOf span(0)
        }
      }
    }
  }

  def "Kubernetes instrumentation handles errors on an asynchronous call"() {
    given:
    server.enqueue(HttpResponse.of(HttpStatus.valueOf(451), MediaType.PLAIN_TEXT_UTF_8, "42"))

    when:
    def exception = new AtomicReference<Exception>()
    def latch = new CountDownLatch(1)

    runWithSpan("parent") {
      api.connectGetNamespacedPodProxyAsync("name", "namespace", "path", new ApiCallbackTemplate() {
        @Override
        void onFailure(ApiException e, int statusCode, Map<String, List<String>> responseHeaders) {
          exception.set(e)
          latch.countDown()
          runInternalSpan("callback")
        }
      })
    }

    then:
    latch.await()
    exception.get() != null
    server.takeRequest().request().headers().get("traceparent") != null

    assertTraces(1) {
      trace(0, 3) {
        span(0) {
          name "parent"
          kind SpanKind.INTERNAL
          hasNoParent()
        }
        apiClientSpan(it, 1, "get  pods/proxy", "${server.httpUri()}/api/v1/namespaces/namespace/pods/name/proxy?path=path", 451, exception.get())
        span(2) {
          name "callback"
          kind SpanKind.INTERNAL
          childOf span(0)
        }
      }
    }
  }

  private void apiClientSpan(TraceAssert trace, int index, String spanName, String url, int statusCode, Throwable exception = null) {
    boolean hasFailed = exception != null
    trace.span(index) {
      name spanName
      kind CLIENT
      childOf trace.span(0)
      if (hasFailed) {
        status ERROR
        errorEvent exception.class, exception.message
      }
      attributes {
        "$SemanticAttributes.HTTP_URL.key" url
        "$SemanticAttributes.HTTP_FLAVOR.key" "1.1"
        "$SemanticAttributes.HTTP_METHOD.key" "GET"
        "$SemanticAttributes.HTTP_USER_AGENT" TEST_USER_AGENT
        "$SemanticAttributes.HTTP_STATUS_CODE" statusCode
        "$SemanticAttributes.NET_TRANSPORT" IP_TCP
        "$SemanticAttributes.NET_PEER_NAME" "127.0.0.1"
        "$SemanticAttributes.NET_PEER_PORT" server.httpPort()
        "kubernetes-client.namespace" "namespace"
        "kubernetes-client.name" "name"
      }
    }
  }

  static class ApiCallbackTemplate implements ApiCallback<String> {
    @Override
    void onFailure(ApiException e, int statusCode, Map<String, List<String>> responseHeaders) {}

    @Override
    void onSuccess(String result, int statusCode, Map<String, List<String>> responseHeaders) {}

    @Override
    void onUploadProgress(long bytesWritten, long contentLength, boolean done) {}

    @Override
    void onDownloadProgress(long bytesRead, long contentLength, boolean done) {}
  }
}
