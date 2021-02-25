/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static io.opentelemetry.api.trace.SpanKind.CLIENT
import static io.opentelemetry.instrumentation.test.server.http.TestHttpServer.httpServer
import static io.opentelemetry.instrumentation.test.utils.TraceUtils.basicSpan
import static io.opentelemetry.instrumentation.test.utils.TraceUtils.runUnderTrace

import io.kubernetes.client.openapi.ApiCallback
import io.kubernetes.client.openapi.ApiClient
import io.kubernetes.client.openapi.ApiException
import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.instrumentation.test.asserts.TraceAssert
import io.opentelemetry.instrumentation.test.server.http.TestHttpServer
import io.opentelemetry.instrumentation.test.utils.OkHttpUtils
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import okhttp3.Request
import spock.lang.AutoCleanup
import spock.lang.Shared

class KubernetesClientTest extends AgentInstrumentationSpecification {
  @Shared
  def httpClient = OkHttpUtils.client()

  @Shared
  def responseStatus = new AtomicInteger()

  @AutoCleanup
  @Shared
  TestHttpServer server

  def setup() {
    // Lazy-load server to allow traits to initialize first.
    if (server == null) {
      server = httpServer {
        handlers {
          all {
            response.status(responseStatus.get()).send("42")
          }
        }
      }
    }
  }

  def "Kubernetes span is registered on a synchronous call"() {
    given:
    def url = server.address.toString() + "/apis/apps/v1/namespaces/default/deployments/foo"
    def request = new Request.Builder()
      .get()
      .url(url)
      .build()
    def call = httpClient.newCall(request)

    responseStatus.set(200)

    when:
    def response = runUnderTrace("parent") {
      new ApiClient().execute(call, Integer)
    }

    then:
    response.data == 42

    assertTraces(1) {
      trace(0, 2) {
        basicSpan(it, 0, "parent")
        apiClientSpan(it, url)
      }
    }
  }

  def "Kubernetes instrumentation handles errors on a synchronous call"() {
    given:
    def url = server.address.toString() + "/apis/apps/v1/namespaces/default/deployments/foo"
    def request = new Request.Builder()
      .get()
      .url(url)
      .build()
    def call = httpClient.newCall(request)

    responseStatus.set(451)

    when:
    runUnderTrace("parent") {
      new ApiClient().execute(call, Integer)
    }

    then:
    def exception = thrown(ApiException)

    assertTraces(1) {
      trace(0, 2) {
        basicSpan(it, 0, "parent", null, exception)
        apiClientSpan(it, url, exception)
      }
    }
  }

  def "Kubernetes span is registered on an asynchronous call"() {
    given:
    def url = server.address.toString() + "/apis/apps/v1/namespaces/default/deployments/foo"
    def request = new Request.Builder()
      .get()
      .url(url)
      .build()
    def call = httpClient.newCall(request)

    responseStatus.set(200)

    when:
    def responseBody = new AtomicInteger()
    def latch = new CountDownLatch(1)

    runUnderTrace("parent") {
      new ApiClient().executeAsync(call, Integer, new ApiCallbackTemplate() {
        @Override
        void onSuccess(Integer result, int statusCode, Map<String, List<String>> responseHeaders) {
          responseBody.set(result)
          latch.countDown()
        }
      })
    }

    then:
    latch.await()
    responseBody.get() == 42

    assertTraces(1) {
      trace(0, 2) {
        basicSpan(it, 0, "parent")
        apiClientSpan(it, url)
      }
    }
  }

  def "Kubernetes instrumentation handles errors on an asynchronous call"() {
    given:
    def url = server.address.toString() + "/apis/apps/v1/namespaces/default/deployments/foo"
    def request = new Request.Builder()
      .get()
      .url(url)
      .build()
    def call = httpClient.newCall(request)

    responseStatus.set(451)

    when:
    def exception = new AtomicReference<Exception>()
    def latch = new CountDownLatch(1)

    runUnderTrace("parent") {
      new ApiClient().executeAsync(call, Integer, new ApiCallbackTemplate() {
        @Override
        void onFailure(ApiException e, int statusCode, Map<String, List<String>> responseHeaders) {
          exception.set(e)
          latch.countDown()
        }
      })
    }

    then:
    latch.await()
    exception.get() != null

    assertTraces(1) {
      trace(0, 2) {
        basicSpan(it, 0, "parent")
        apiClientSpan(it, url, exception.get())
      }
    }
  }

  private void apiClientSpan(TraceAssert trace, String url, Throwable exception = null) {
    boolean hasFailed = exception != null
    trace.span(1) {
      name "get apps/v1 deployments"
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
        "$SemanticAttributes.HTTP_STATUS_CODE" responseStatus.get()
        "$SemanticAttributes.NET_TRANSPORT" "IP.TCP"
        "$SemanticAttributes.NET_PEER_NAME" server.address.host
        "$SemanticAttributes.NET_PEER_PORT" server.address.port
        "kubernetes-client.namespace" "default"
        "kubernetes-client.name" "foo"
      }
    }
  }

  static class ApiCallbackTemplate implements ApiCallback<Integer> {
    @Override
    void onFailure(ApiException e, int statusCode, Map<String, List<String>> responseHeaders) {}

    @Override
    void onSuccess(Integer result, int statusCode, Map<String, List<String>> responseHeaders) {}

    @Override
    void onUploadProgress(long bytesWritten, long contentLength, boolean done) {}

    @Override
    void onDownloadProgress(long bytesRead, long contentLength, boolean done) {}
  }
}
