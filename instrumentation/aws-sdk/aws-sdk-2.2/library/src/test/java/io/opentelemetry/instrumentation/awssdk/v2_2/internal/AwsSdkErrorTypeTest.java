/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2.internal;

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
import static io.opentelemetry.semconv.incubating.AwsIncubatingAttributes.AWS_REQUEST_ID;
import static io.opentelemetry.semconv.incubating.AwsIncubatingAttributes.AWS_S3_BUCKET;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_METHOD;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SERVICE;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SYSTEM;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SYSTEM_NAME;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.instrumentation.awssdk.v2_2.AwsSdkTelemetry;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import software.amazon.awssdk.awscore.DefaultAwsResponseMetadata;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.ClientType;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.interceptor.Context;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor;
import software.amazon.awssdk.core.interceptor.SdkExecutionAttribute;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

@SuppressWarnings("deprecation") // using deprecated semconv
class AwsSdkErrorTypeTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  private final ExecutionInterceptor interceptor =
      AwsSdkTelemetry.create(testing.getOpenTelemetry()).createExecutionInterceptor();
  private final GetObjectRequest request =
      GetObjectRequest.builder().bucket("bucket").key("key").build();
  private final SdkHttpRequest httpRequest =
      SdkHttpRequest.builder()
          .uri(URI.create("https://s3.amazonaws.com"))
          .method(SdkHttpMethod.GET)
          .build();

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"NoSuchBucket", "AccessDenied"})
  void serviceError(String code) {
    AwsServiceException error =
        S3Exception.builder()
            .message("service failure")
            .statusCode(403)
            .awsErrorDetails(AwsErrorDetails.builder().errorCode(code).build())
            .build();
    assertThat(error).isExactlyInstanceOf(S3Exception.class);
    fail(error, code == null || code.isEmpty() ? S3Exception.class.getName() : code);
  }

  @Test
  void serviceErrorWithoutDetails() {
    fail(S3Exception.builder().message("service failure").build(), S3Exception.class.getName());
  }

  @Test
  void clientError() {
    fail(SdkClientException.create("client failure"), SdkClientException.class.getName());
  }

  @Test
  void success() {
    ExecutionAttributes attributes = startRequest();
    Context.AfterExecution context = mock(Context.AfterExecution.class);
    when(context.request()).thenReturn(request);
    when(context.httpRequest()).thenReturn(httpRequest);
    when(context.httpResponse()).thenReturn(SdkHttpResponse.builder().statusCode(200).build());
    when(context.response())
        .thenReturn(
            GetObjectResponse.builder()
                .responseMetadata(
                    DefaultAwsResponseMetadata.create(
                        singletonMap("x-amz-request-id", "request-id")))
                .build());
    interceptor.afterExecution(context, attributes);

    assertThat(TracingExecutionInterceptor.getContext(attributes)).isNull();
    assertSpan(null, 200L, null);
  }

  private void fail(Exception error, String errorType) {
    ExecutionAttributes attributes = startRequest();
    Context.FailedExecution context = mock(Context.FailedExecution.class);
    when(context.exception()).thenReturn(error);
    interceptor.onExecutionFailure(context, attributes);

    assertThat(TracingExecutionInterceptor.getContext(attributes)).isNull();
    assertSpan(error, null, emitPreviewRpcSemconv() ? errorType : null);
  }

  private ExecutionAttributes startRequest() {
    ExecutionAttributes attributes = new ExecutionAttributes();
    attributes.putAttribute(SdkExecutionAttribute.CLIENT_TYPE, ClientType.SYNC);
    attributes.putAttribute(SdkExecutionAttribute.SERVICE_NAME, "S3");
    attributes.putAttribute(SdkExecutionAttribute.OPERATION_NAME, "GetObject");
    Context.ModifyRequest modifyRequest = mock(Context.ModifyRequest.class);
    when(modifyRequest.request()).thenReturn(request);
    interceptor.modifyRequest(modifyRequest, attributes);
    interceptor.afterMarshalling(mock(Context.AfterMarshalling.class), attributes);
    Context.BeforeTransmission beforeTransmission = mock(Context.BeforeTransmission.class);
    when(beforeTransmission.httpRequest()).thenReturn(httpRequest);
    interceptor.beforeTransmission(beforeTransmission, attributes);
    assertThat(TracingExecutionInterceptor.getContext(attributes)).isNotNull();
    return attributes;
  }

  private static void assertSpan(Exception error, Long statusCode, String errorType) {
    assertThat(io.opentelemetry.context.Context.current())
        .isSameAs(io.opentelemetry.context.Context.root());
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
                            equalTo(RPC_SERVICE, emitOldRpcSemconv() ? "S3" : null),
                            equalTo(RPC_METHOD, emitPreviewRpcSemconv() ? null : "GetObject"),
                            equalTo(RPC_SYSTEM_NAME, emitPreviewRpcSemconv() ? "aws-api" : null),
                            equalTo(AWS_S3_BUCKET, "bucket"),
                            equalTo(AWS_REQUEST_ID, error == null ? "request-id" : null),
                            equalTo(ERROR_TYPE, errorType))));
  }
}
