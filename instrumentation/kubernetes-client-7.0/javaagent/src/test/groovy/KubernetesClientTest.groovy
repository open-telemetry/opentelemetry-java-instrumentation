/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static io.opentelemetry.api.trace.SpanKind.CLIENT
import static io.opentelemetry.instrumentation.test.server.http.TestHttpServer.distributedRequestSpan
import static io.opentelemetry.instrumentation.test.server.http.TestHttpServer.httpServer
import static io.opentelemetry.instrumentation.test.utils.TraceUtils.basicSpan
import static io.opentelemetry.instrumentation.test.utils.TraceUtils.runInternalSpan
import static io.opentelemetry.instrumentation.test.utils.TraceUtils.runUnderTrace

import io.kubernetes.client.openapi.ApiCallback
import io.kubernetes.client.openapi.ApiClient
import io.kubernetes.client.openapi.ApiException
import io.kubernetes.client.openapi.apis.CoreV1Api
import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.instrumentation.test.asserts.TraceAssert
import io.opentelemetry.instrumentation.test.server.http.TestHttpServer
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import spock.lang.AutoCleanup
import spock.lang.Shared

class KubernetesClientTest extends AgentInstrumentationSpecification {
  private static final String TEST_USER_AGENT = "test-user-agent"

  @Shared
  def responseStatus = new AtomicInteger()

  @AutoCleanup
  @Shared
  TestHttpServer server

  @Shared
  CoreV1Api api

  def setup() {
    // Lazy-load server to allow traits to initialize first.
    if (server == null) {
      server = httpServer {
        handlers {
          all {
            handleDistributedRequest()
            response.status(responseStatus.get()).send("42")
          }
        }
      }
    }

    if (api == null) {
      def apiClient = new ApiClient()
      apiClient.setUserAgent(TEST_USER_AGENT)
      apiClient.basePath = server.address.toString()
      api = new CoreV1Api(apiClient)
    }
  }

  def "Kubernetes span is registered on a synchronous call"() {
    given:
    responseStatus.set(200)

    when:
    def response = runUnderTrace("parent") {
      api.connectGetNamespacedPodProxy("name", "namespace", "path")
    }

    then:
    response == "42"

    assertTraces(1) {
      trace(0, 3) {
        basicSpan(it, 0, "parent")
        apiClientSpan(it, 1, "get  pods/proxy", "${server.address}/api/v1/namespaces/namespace/pods/name/proxy?path=path")
        distributedRequestSpan(it, 2, span(1))
      }
    }
  }

  def "Kubernetes instrumentation handles errors on a synchronous call"() {
    given:
    responseStatus.set(451)

    when:
    runUnderTrace("parent") {
      api.connectGetNamespacedPodProxy("name", "namespace", "path")
    }

    then:
    def exception = thrown(ApiException)

    assertTraces(1) {
      trace(0, 3) {
        basicSpan(it, 0, "parent", null, exception)
        apiClientSpan(it, 1, "get  pods/proxy", "${server.address}/api/v1/namespaces/namespace/pods/name/proxy?path=path", exception)
        distributedRequestSpan(it, 2, span(1))
      }
    }
  }

  def "Kubernetes span is registered on an asynchronous call"() {
    given:
    responseStatus.set(200)

    when:
    def responseBody = new AtomicReference<String>()
    def latch = new CountDownLatch(1)

    runUnderTrace("parent") {
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

    assertTraces(1) {
      trace(0, 4) {
        basicSpan(it, 0, "parent")
        apiClientSpan(it, 1, "get  pods/proxy", "${server.address}/api/v1/namespaces/namespace/pods/name/proxy?path=path")
        distributedRequestSpan(it, 2, span(1))
        basicSpan(it, 3, "callback", span(0))
      }
    }
  }

  def "Kubernetes instrumentation handles errors on an asynchronous call"() {
    given:
    responseStatus.set(451)

    when:
    def exception = new AtomicReference<Exception>()
    def latch = new CountDownLatch(1)

    runUnderTrace("parent") {
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

    assertTraces(1) {
      trace(0, 4) {
        basicSpan(it, 0, "parent")
        apiClientSpan(it, 1, "get  pods/proxy", "${server.address}/api/v1/namespaces/namespace/pods/name/proxy?path=path", exception.get())
        distributedRequestSpan(it, 2, span(1))
        basicSpan(it, 3, "callback", span(0))
      }
    }
  }

  private void apiClientSpan(TraceAssert trace, int index, String spanName, String url, Throwable exception = null) {
    boolean hasFailed = exception != null
    trace.span(index) {
      name spanName
      kind CLIENT
      childOf trace.span(0)
      errored hasFailed
      if (hasFailed) {
        errorEvent exception.class, exception.message
      }
      attributes {
        "$SemanticAttributes.HTTP_URL.key" url
        "$SemanticAttributes.HTTP_FLAVOR.key" "1.1"
        "$SemanticAttributes.HTTP_METHOD.key" "GET"
        "$SemanticAttributes.HTTP_USER_AGENT" TEST_USER_AGENT
        "$SemanticAttributes.HTTP_STATUS_CODE" responseStatus.get()
        "$SemanticAttributes.NET_TRANSPORT" "IP.TCP"
        "$SemanticAttributes.NET_PEER_NAME" server.address.host
        "$SemanticAttributes.NET_PEER_PORT" server.address.port
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
