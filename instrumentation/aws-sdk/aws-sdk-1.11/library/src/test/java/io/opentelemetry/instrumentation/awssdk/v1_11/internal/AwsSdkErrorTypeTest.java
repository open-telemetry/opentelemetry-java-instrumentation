/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11.internal;

import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.instrumentation.api.internal.SemconvExceptionSignal.emitExceptionAsSpanEvents;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldRpcSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitPreviewRpcSemconv;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_REQUEST_METHOD;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_RESPONSE_STATUS_CODE;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.UrlAttributes.URL_FULL;
import static io.opentelemetry.semconv.incubating.AwsIncubatingAttributes.AWS_S3_BUCKET;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_METHOD;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SERVICE;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SYSTEM;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SYSTEM_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.amazonaws.AmazonClientException;
import com.amazonaws.DefaultRequest;
import com.amazonaws.Request;
import com.amazonaws.Response;
import com.amazonaws.handlers.RequestHandler2;
import com.amazonaws.http.HttpMethodName;
import com.amazonaws.http.HttpResponse;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.GetObjectRequest;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.awssdk.v1_11.AwsSdkTelemetry;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@SuppressWarnings("deprecation") // using deprecated semconv
class AwsSdkErrorTypeTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  private final RequestHandler2 handler =
      AwsSdkTelemetry.create(testing.getOpenTelemetry()).createRequestHandler();

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"NoSuchBucket", "AccessDenied"})
  void serviceError(String code) {
    AmazonS3Exception error = new AmazonS3Exception("service failure");
    error.setErrorCode(code);
    error.setStatusCode(403);
    Request<?> request = startRequest();
    handler.afterError(request, response(403), error);

    assertThat(AwsSdkTelemetry.getOpenTelemetryContext(request)).isNull();
    assertSpan(
        error,
        403L,
        emitPreviewRpcSemconv()
            ? (code == null || code.isEmpty() ? AmazonS3Exception.class.getName() : code)
            : "403");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"NoSuchBucket", "AccessDenied"})
  void serviceErrorWithoutResponse(String code) {
    AmazonS3Exception error = new AmazonS3Exception("service failure");
    error.setErrorCode(code);
    Request<?> request = startRequest();
    handler.afterError(request, null, error);

    assertThat(AwsSdkTelemetry.getOpenTelemetryContext(request)).isNull();
    assertSpan(
        error,
        null,
        emitPreviewRpcSemconv() && code != null && !code.isEmpty()
            ? code
            : AmazonS3Exception.class.getName());
  }

  @Test
  void clientError() {
    AmazonClientException error = new AmazonClientException("client failure");
    Request<?> request = startRequest();
    handler.afterError(request, null, error);

    assertThat(AwsSdkTelemetry.getOpenTelemetryContext(request)).isNull();
    assertSpan(error, null, AmazonClientException.class.getName());
  }

  @Test
  void success() {
    Request<?> request = startRequest();
    handler.afterResponse(request, response(200));

    assertThat(AwsSdkTelemetry.getOpenTelemetryContext(request)).isNull();
    assertSpan(null, 200L, null);
  }

  private Request<?> startRequest() {
    Request<?> request = new DefaultRequest<>(new GetObjectRequest("bucket", "key"), "Amazon S3");
    request.setEndpoint(URI.create("https://s3.amazonaws.com"));
    request.setHttpMethod(HttpMethodName.GET);
    handler.beforeRequest(request);
    assertThat(AwsSdkTelemetry.getOpenTelemetryContext(request)).isNotNull();
    return request;
  }

  private static Response<?> response(int statusCode) {
    HttpResponse httpResponse = mock(HttpResponse.class);
    when(httpResponse.getStatusCode()).thenReturn(statusCode);
    return new Response<>(null, httpResponse);
  }

  private static void assertSpan(Exception error, Long statusCode, String errorType) {
    assertThat(Context.current()).isSameAs(Context.root());
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("S3.GetObject")
                        .hasKind(CLIENT)
                        .hasNoParent()
                        .hasStatus(error == null ? StatusData.unset() : StatusData.error())
                        .hasException(emitExceptionAsSpanEvents() ? error : null)
                        .hasAttributesSatisfyingExactly(
                            equalTo(URL_FULL, "https://s3.amazonaws.com"),
                            equalTo(HTTP_REQUEST_METHOD, "GET"),
                            equalTo(HTTP_RESPONSE_STATUS_CODE, statusCode),
                            equalTo(SERVER_ADDRESS, "s3.amazonaws.com"),
                            equalTo(SERVER_PORT, 443),
                            equalTo(RPC_SYSTEM, emitOldRpcSemconv() ? "aws-api" : null),
                            equalTo(RPC_SERVICE, emitOldRpcSemconv() ? "Amazon S3" : null),
                            equalTo(RPC_METHOD, emitPreviewRpcSemconv() ? null : "GetObject"),
                            equalTo(RPC_SYSTEM_NAME, emitPreviewRpcSemconv() ? "aws-api" : null),
                            equalTo(AWS_S3_BUCKET, "bucket"),
                            equalTo(ERROR_TYPE, errorType))));
  }
}
