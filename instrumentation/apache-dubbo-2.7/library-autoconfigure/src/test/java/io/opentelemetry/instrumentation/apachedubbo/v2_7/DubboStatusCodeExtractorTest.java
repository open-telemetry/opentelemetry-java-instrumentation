/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v2_7;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusBuilder;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.instrumentation.apachedubbo.v2_7.internal.DubboStatusCodeHolder;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.RpcInvocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@SuppressWarnings("deprecation") // RpcContext.getContext() is deprecated in newer Dubbo
class DubboStatusCodeExtractorTest {

  private static final VirtualField<RpcInvocation, DubboStatusCodeHolder> statusCodeField =
      VirtualField.find(RpcInvocation.class, DubboStatusCodeHolder.class);

  private RpcInvocation invocation;
  private DubboRequest request;
  private Result successResult;
  private Result errorResult;

  @BeforeEach
  void setUp() {
    invocation = new RpcInvocation();

    RpcContext rpcContext = RpcContext.getContext();
    rpcContext.setUrl(URL.valueOf("dubbo://localhost:20880/TestService"));
    request = DubboRequest.create(invocation, rpcContext);

    successResult = mock(Result.class);
    when(successResult.hasException()).thenReturn(false);

    errorResult = mock(Result.class);
    when(errorResult.hasException()).thenReturn(true);
    when(errorResult.getException()).thenReturn(new RuntimeException("service error"));
  }

  // --------------- resolveStatusCode tests ---------------

  @Test
  void resolveStatusCode_successfulCallWithoutVirtualField_returnsNull() {
    assertThat(DubboSpanStatusExtractor.resolveStatusCode(request, successResult, null)).isNull();
  }

  @Test
  void resolveStatusCode_errorWithoutVirtualField_returnsNull() {
    RuntimeException error = new RuntimeException("some error");
    assertThat(DubboSpanStatusExtractor.resolveStatusCode(request, errorResult, error)).isNull();
  }

  @ParameterizedTest
  @CsvSource({
    "OK",
    "SERVER_ERROR",
    "SERVER_TIMEOUT",
    "CLIENT_TIMEOUT",
    "CHANNEL_INACTIVE",
    "BAD_REQUEST",
    "BAD_RESPONSE",
    "SERVICE_NOT_FOUND",
    "SERVICE_ERROR",
    "CLIENT_ERROR",
    "SERVER_THREADPOOL_EXHAUSTED_ERROR",
    "SERIALIZATION_ERROR"
  })
  void resolveStatusCode_dubbo2StatusFromVirtualField(String statusCode) {
    boolean isServerError =
        statusCode.equals("SERVER_ERROR")
            || statusCode.equals("SERVER_TIMEOUT")
            || statusCode.equals("SERVICE_ERROR")
            || statusCode.equals("SERVER_THREADPOOL_EXHAUSTED_ERROR");

    statusCodeField.set(invocation, new DubboStatusCodeHolder(statusCode, isServerError));

    assertThat(DubboSpanStatusExtractor.resolveStatusCode(request, successResult, null))
        .isEqualTo(statusCode);
  }

  @ParameterizedTest
  @CsvSource({
    "OK",
    "CANCELLED",
    "UNKNOWN",
    "INVALID_ARGUMENT",
    "DEADLINE_EXCEEDED",
    "NOT_FOUND",
    "PERMISSION_DENIED",
    "RESOURCE_EXHAUSTED",
    "UNIMPLEMENTED",
    "INTERNAL",
    "UNAVAILABLE",
    "DATA_LOSS",
    "UNAUTHENTICATED"
  })
  void resolveStatusCode_tripleStatusFromVirtualField(String statusCode) {
    boolean isServerError =
        statusCode.equals("UNKNOWN")
            || statusCode.equals("DEADLINE_EXCEEDED")
            || statusCode.equals("UNIMPLEMENTED")
            || statusCode.equals("INTERNAL")
            || statusCode.equals("UNAVAILABLE")
            || statusCode.equals("DATA_LOSS");

    statusCodeField.set(invocation, new DubboStatusCodeHolder(statusCode, isServerError));

    assertThat(DubboSpanStatusExtractor.resolveStatusCode(request, null, null))
        .isEqualTo(statusCode);
  }

  // --------------- CLIENT span status tests ---------------

  @ParameterizedTest
  @CsvSource({
    "SERVER_ERROR",
    "SERVER_TIMEOUT",
    "CLIENT_TIMEOUT",
    "BAD_REQUEST",
    "BAD_RESPONSE",
    "SERVICE_NOT_FOUND",
    "SERVICE_ERROR",
    "CLIENT_ERROR",
    "CHANNEL_INACTIVE",
    "SERVER_THREADPOOL_EXHAUSTED_ERROR",
    "SERIALIZATION_ERROR"
  })
  void clientSpanStatus_nonOkDubbo2Status_isError(String statusCode) {
    statusCodeField.set(invocation, new DubboStatusCodeHolder(statusCode, false));
    StatusCode result = extractClientSpanStatus(request, successResult, null);
    assertThat(result).isEqualTo(StatusCode.ERROR);
  }

  @Test
  void clientSpanStatus_okStatus_isUnset() {
    statusCodeField.set(invocation, new DubboStatusCodeHolder("OK", false));
    StatusCode result = extractClientSpanStatus(request, successResult, null);
    assertThat(result).isEqualTo(StatusCode.UNSET);
  }

  @ParameterizedTest
  @CsvSource({
    "CANCELLED",
    "UNKNOWN",
    "INVALID_ARGUMENT",
    "DEADLINE_EXCEEDED",
    "NOT_FOUND",
    "PERMISSION_DENIED",
    "RESOURCE_EXHAUSTED",
    "UNIMPLEMENTED",
    "INTERNAL",
    "UNAVAILABLE",
    "DATA_LOSS",
    "UNAUTHENTICATED"
  })
  void clientSpanStatus_nonOkTripleStatus_isError(String statusCode) {
    statusCodeField.set(invocation, new DubboStatusCodeHolder(statusCode, false));
    StatusCode result = extractClientSpanStatus(request, successResult, null);
    assertThat(result).isEqualTo(StatusCode.ERROR);
  }

  @Test
  void clientSpanStatus_noStatusCodeButExceptionPresent_isError() {
    StatusCode result =
        extractClientSpanStatus(request, errorResult, new RuntimeException("fail"));
    assertThat(result).isEqualTo(StatusCode.ERROR);
  }

  // --------------- SERVER span status tests ---------------

  @ParameterizedTest
  @CsvSource({
    "SERVER_ERROR, true",
    "SERVER_TIMEOUT, true",
    "SERVICE_ERROR, true",
    "SERVER_THREADPOOL_EXHAUSTED_ERROR, true",
    "CLIENT_TIMEOUT, false",
    "BAD_REQUEST, false",
    "BAD_RESPONSE, false",
    "SERVICE_NOT_FOUND, false",
    "CLIENT_ERROR, false",
    "CHANNEL_INACTIVE, false",
    "SERIALIZATION_ERROR, false",
    "OK, false"
  })
  void serverSpanStatus_dubbo2Status(String statusCode, boolean expectError) {
    boolean isServerError =
        statusCode.equals("SERVER_ERROR")
            || statusCode.equals("SERVER_TIMEOUT")
            || statusCode.equals("SERVICE_ERROR")
            || statusCode.equals("SERVER_THREADPOOL_EXHAUSTED_ERROR");

    statusCodeField.set(invocation, new DubboStatusCodeHolder(statusCode, isServerError));
    StatusCode result = extractServerSpanStatus(request, successResult, null);
    assertThat(result).isEqualTo(expectError ? StatusCode.ERROR : StatusCode.UNSET);
  }

  @ParameterizedTest
  @CsvSource({
    "UNKNOWN, true",
    "DEADLINE_EXCEEDED, true",
    "UNIMPLEMENTED, true",
    "INTERNAL, true",
    "UNAVAILABLE, true",
    "DATA_LOSS, true",
    "OK, false",
    "CANCELLED, false",
    "INVALID_ARGUMENT, false",
    "NOT_FOUND, false",
    "PERMISSION_DENIED, false",
    "RESOURCE_EXHAUSTED, false",
    "UNAUTHENTICATED, false"
  })
  void serverSpanStatus_tripleStatus(String statusCode, boolean expectError) {
    boolean isServerError =
        statusCode.equals("UNKNOWN")
            || statusCode.equals("DEADLINE_EXCEEDED")
            || statusCode.equals("UNIMPLEMENTED")
            || statusCode.equals("INTERNAL")
            || statusCode.equals("UNAVAILABLE")
            || statusCode.equals("DATA_LOSS");

    statusCodeField.set(invocation, new DubboStatusCodeHolder(statusCode, isServerError));
    StatusCode result = extractServerSpanStatus(request, successResult, null);
    assertThat(result).isEqualTo(expectError ? StatusCode.ERROR : StatusCode.UNSET);
  }

  @Test
  void serverSpanStatus_noStatusCodeButExceptionPresent_isError() {
    StatusCode result =
        extractServerSpanStatus(request, errorResult, new RuntimeException("fail"));
    assertThat(result).isEqualTo(StatusCode.ERROR);
  }

  // --------------- getErrorType tests ---------------

  @ParameterizedTest
  @CsvSource({
    "SERVER_ERROR, true",
    "SERVER_TIMEOUT, true",
    "SERVICE_ERROR, true",
    "SERVER_THREADPOOL_EXHAUSTED_ERROR, true",
    "OK, false",
    "CLIENT_TIMEOUT, false",
    "BAD_REQUEST, false",
    "CLIENT_ERROR, false"
  })
  void getErrorType_dubbo2ServerErrorStatus(String statusCode, boolean expectStatusCodeReturned) {
    boolean isServerError =
        statusCode.equals("SERVER_ERROR")
            || statusCode.equals("SERVER_TIMEOUT")
            || statusCode.equals("SERVICE_ERROR")
            || statusCode.equals("SERVER_THREADPOOL_EXHAUSTED_ERROR");

    statusCodeField.set(invocation, new DubboStatusCodeHolder(statusCode, isServerError));

    String errorType =
        DubboRpcAttributesGetter.INSTANCE.getErrorType(request, successResult, null);
    if (expectStatusCodeReturned) {
      assertThat(errorType).isEqualTo(statusCode);
    } else {
      assertThat(errorType).isNull();
    }
  }

  @ParameterizedTest
  @CsvSource({
    "UNKNOWN, true",
    "DEADLINE_EXCEEDED, true",
    "UNIMPLEMENTED, true",
    "INTERNAL, true",
    "UNAVAILABLE, true",
    "DATA_LOSS, true",
    "OK, false",
    "CANCELLED, false",
    "NOT_FOUND, false",
    "PERMISSION_DENIED, false"
  })
  void getErrorType_tripleServerErrorStatus(String statusCode, boolean expectStatusCodeReturned) {
    boolean isServerError =
        statusCode.equals("UNKNOWN")
            || statusCode.equals("DEADLINE_EXCEEDED")
            || statusCode.equals("UNIMPLEMENTED")
            || statusCode.equals("INTERNAL")
            || statusCode.equals("UNAVAILABLE")
            || statusCode.equals("DATA_LOSS");

    statusCodeField.set(invocation, new DubboStatusCodeHolder(statusCode, isServerError));

    String errorType =
        DubboRpcAttributesGetter.INSTANCE.getErrorType(request, successResult, null);
    if (expectStatusCodeReturned) {
      assertThat(errorType).isEqualTo(statusCode);
    } else {
      assertThat(errorType).isNull();
    }
  }

  @Test
  void getErrorType_noVirtualFieldNoError_returnsNull() {
    String errorType =
        DubboRpcAttributesGetter.INSTANCE.getErrorType(request, successResult, null);
    assertThat(errorType).isNull();
  }

  @Test
  void getErrorType_noVirtualFieldWithError_returnsNull() {
    RuntimeException error = new RuntimeException("some error");
    String errorType = DubboRpcAttributesGetter.INSTANCE.getErrorType(request, errorResult, error);
    assertThat(errorType).isNull();
  }

  // --------------- helper methods ---------------

  private static StatusCode extractClientSpanStatus(
      DubboRequest request, @Nullable Result response, @Nullable Throwable error) {
    return extractSpanStatus(DubboSpanStatusExtractor.CLIENT, request, response, error);
  }

  private static StatusCode extractServerSpanStatus(
      DubboRequest request, @Nullable Result response, @Nullable Throwable error) {
    return extractSpanStatus(DubboSpanStatusExtractor.SERVER, request, response, error);
  }

  private static StatusCode extractSpanStatus(
      DubboSpanStatusExtractor extractor,
      DubboRequest request,
      @Nullable Result response,
      @Nullable Throwable error) {
    AtomicReference<StatusCode> statusRef = new AtomicReference<>(StatusCode.UNSET);
    SpanStatusBuilder builder =
        new SpanStatusBuilder() {
          @Override
          public SpanStatusBuilder setStatus(StatusCode statusCode, String description) {
            statusRef.set(statusCode);
            return this;
          }
        };
    extractor.extract(builder, request, response, error);
    return statusRef.get();
  }
}
