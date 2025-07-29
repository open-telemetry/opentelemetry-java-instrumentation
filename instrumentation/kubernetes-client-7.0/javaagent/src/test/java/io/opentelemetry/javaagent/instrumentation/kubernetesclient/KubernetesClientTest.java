/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kubernetesclient;

import static io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil.orderByRootSpanName;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_REQUEST_METHOD;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_RESPONSE_STATUS_CODE;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.UrlAttributes.URL_FULL;
import static org.assertj.core.api.Assertions.assertThat;

import io.kubernetes.client.openapi.ApiCallback;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.testing.internal.armeria.common.HttpResponse;
import io.opentelemetry.testing.internal.armeria.common.HttpStatus;
import io.opentelemetry.testing.internal.armeria.common.MediaType;
import io.opentelemetry.testing.internal.armeria.testing.junit5.server.mock.MockWebServerExtension;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class KubernetesClientTest {

  private static final String TEST_USER_AGENT = "test-user-agent";

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private final MockWebServerExtension mockWebServer = new MockWebServerExtension();

  private CoreV1Api coreV1Api;

  @BeforeEach
  void beforeEach() {
    mockWebServer.start();
    ApiClient apiClient = new ApiClient();
    apiClient.setUserAgent(TEST_USER_AGENT);
    apiClient.setBasePath(mockWebServer.httpUri().toString());
    coreV1Api = new CoreV1Api(apiClient);
  }

  @AfterEach
  void afterEach() {
    mockWebServer.stop();
  }

  @Test
  void synchronousCall() throws ApiException {
    mockWebServer.enqueue(HttpResponse.of(HttpStatus.OK, MediaType.PLAIN_TEXT_UTF_8, "42"));
    String response =
        testing.runWithSpan(
            "parent", () -> coreV1Api.connectGetNamespacedPodProxy("name", "namespace", "path"));

    assertThat(response).isEqualTo("42");
    assertThat(mockWebServer.takeRequest().request().headers().get("traceparent")).isNotBlank();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName("get pods/proxy")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                URL_FULL,
                                mockWebServer.httpUri()
                                    + "/api/v1/namespaces/namespace/pods/name/proxy?path=path"),
                            equalTo(HTTP_REQUEST_METHOD, "GET"),
                            equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
                            equalTo(SERVER_ADDRESS, "127.0.0.1"),
                            equalTo(SERVER_PORT, mockWebServer.httpPort()),
                            equalTo(
                                AttributeKey.stringKey("kubernetes-client.namespace"), "namespace"),
                            equalTo(AttributeKey.stringKey("kubernetes-client.name"), "name"))));
  }

  @Test
  void handleErrorsInSyncCall() {
    mockWebServer.enqueue(
        HttpResponse.of(HttpStatus.valueOf(451), MediaType.PLAIN_TEXT_UTF_8, "42"));
    ApiException exception = null;
    try {
      testing.runWithSpan(
          "parent",
          () -> {
            coreV1Api.connectGetNamespacedPodProxy("name", "namespace", "path");
          });
    } catch (ApiException e) {
      exception = e;
    }
    ApiException apiException = exception;
    assertThat(apiException).isNotNull();
    assertThat(mockWebServer.takeRequest().request().headers().get("traceparent")).isNotBlank();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("parent")
                        .hasKind(SpanKind.INTERNAL)
                        .hasNoParent()
                        .hasStatus(StatusData.error())
                        .hasException(apiException),
                span ->
                    span.hasName("get pods/proxy")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasStatus(StatusData.error())
                        .hasException(apiException)
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                URL_FULL,
                                mockWebServer.httpUri()
                                    + "/api/v1/namespaces/namespace/pods/name/proxy?path=path"),
                            equalTo(HTTP_REQUEST_METHOD, "GET"),
                            equalTo(HTTP_RESPONSE_STATUS_CODE, 451),
                            equalTo(SERVER_ADDRESS, "127.0.0.1"),
                            equalTo(SERVER_PORT, mockWebServer.httpPort()),
                            equalTo(ERROR_TYPE, "451"),
                            equalTo(
                                AttributeKey.stringKey("kubernetes-client.namespace"), "namespace"),
                            equalTo(AttributeKey.stringKey("kubernetes-client.name"), "name"))));
  }

  @Test
  void asynchronousCall() throws ApiException, InterruptedException {
    mockWebServer.enqueue(HttpResponse.of(HttpStatus.OK, MediaType.PLAIN_TEXT_UTF_8, "42"));

    AtomicReference<String> responseBodyReference = new AtomicReference<>();
    CountDownLatch countDownLatch = new CountDownLatch(1);

    testing.runWithSpan(
        "parent",
        () -> {
          coreV1Api.connectGetNamespacedPodProxyAsync(
              "name",
              "namespace",
              "path",
              new ApiCallbackTemplate() {
                @Override
                public void onSuccess(
                    String result, int statusCode, Map<String, List<String>> responseHeaders) {
                  responseBodyReference.set(result);
                  countDownLatch.countDown();
                  testing.runWithSpan("callback", () -> {});
                }
              });
        });

    countDownLatch.await();

    assertThat(responseBodyReference.get()).isEqualTo("42");
    assertThat(mockWebServer.takeRequest().request().headers().get("traceparent")).isNotBlank();

    testing.waitAndAssertSortedTraces(
        orderByRootSpanName("parent", "get pods/proxy", "callback"),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName("get pods/proxy")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                URL_FULL,
                                mockWebServer.httpUri()
                                    + "/api/v1/namespaces/namespace/pods/name/proxy?path=path"),
                            equalTo(HTTP_REQUEST_METHOD, "GET"),
                            equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
                            equalTo(SERVER_ADDRESS, "127.0.0.1"),
                            equalTo(SERVER_PORT, mockWebServer.httpPort()),
                            equalTo(
                                AttributeKey.stringKey("kubernetes-client.namespace"), "namespace"),
                            equalTo(AttributeKey.stringKey("kubernetes-client.name"), "name")),
                span ->
                    span.hasName("callback")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0))));
  }

  @Test
  void handleErrorsInAsynchronousCall() throws ApiException, InterruptedException {

    mockWebServer.enqueue(
        HttpResponse.of(HttpStatus.valueOf(451), MediaType.PLAIN_TEXT_UTF_8, "42"));

    AtomicReference<ApiException> exceptionReference = new AtomicReference<>();
    CountDownLatch countDownLatch = new CountDownLatch(1);

    testing.runWithSpan(
        "parent",
        () -> {
          coreV1Api.connectGetNamespacedPodProxyAsync(
              "name",
              "namespace",
              "path",
              new ApiCallbackTemplate() {
                @Override
                public void onFailure(
                    ApiException e, int statusCode, Map<String, List<String>> responseHeaders) {
                  exceptionReference.set(e);
                  countDownLatch.countDown();
                  testing.runWithSpan("callback", () -> {});
                }
              });
        });

    countDownLatch.await();

    assertThat(exceptionReference.get()).isNotNull();
    assertThat(mockWebServer.takeRequest().request().headers().get("traceparent")).isNotBlank();

    testing.waitAndAssertSortedTraces(
        orderByRootSpanName("parent", "get pods/proxy", "callback"),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName("get pods/proxy")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasStatus(StatusData.error())
                        .hasException(exceptionReference.get())
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                URL_FULL,
                                mockWebServer.httpUri()
                                    + "/api/v1/namespaces/namespace/pods/name/proxy?path=path"),
                            equalTo(HTTP_REQUEST_METHOD, "GET"),
                            equalTo(HTTP_RESPONSE_STATUS_CODE, 451),
                            equalTo(SERVER_ADDRESS, "127.0.0.1"),
                            equalTo(SERVER_PORT, mockWebServer.httpPort()),
                            equalTo(ERROR_TYPE, "451"),
                            equalTo(
                                AttributeKey.stringKey("kubernetes-client.namespace"), "namespace"),
                            equalTo(AttributeKey.stringKey("kubernetes-client.name"), "name")),
                span ->
                    span.hasName("callback")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0))));
  }

  private static class ApiCallbackTemplate implements ApiCallback<String> {
    @Override
    public void onFailure(
        ApiException e, int statusCode, Map<String, List<String>> responseHeaders) {}

    @Override
    public void onSuccess(
        String result, int statusCode, Map<String, List<String>> responseHeaders) {}

    @Override
    public void onUploadProgress(long bytesWritten, long contentLength, boolean done) {}

    @Override
    public void onDownloadProgress(long bytesRead, long contentLength, boolean done) {}
  }
}
