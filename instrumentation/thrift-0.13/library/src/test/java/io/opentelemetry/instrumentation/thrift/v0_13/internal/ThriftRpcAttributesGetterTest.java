/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.thrift.v0_13.internal;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.thrift.v0_13.ThriftRequest;
import io.opentelemetry.instrumentation.thrift.v0_13.ThriftResponse;
import org.apache.thrift.TApplicationException;
import org.apache.thrift.protocol.TProtocolException;
import org.apache.thrift.transport.TTransportException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ThriftRpcAttributesGetterTest {

  private final ThriftRpcAttributesGetter getter = new ThriftRpcAttributesGetter();
  private final ThriftRequest request = new ThriftRequest("method", "service", null);

  @Test
  void rpcMethodUsesRpcServiceName() {
    ThriftRequest request = new ThriftRequest("method", "handler", "idl", null, null, emptyMap());

    assertThat(getter.getService(request)).isEqualTo("handler");
    assertThat(getter.getRpcMethod(request)).isEqualTo("idl/method");
    assertThat(getter.getRpcMethodOriginal(request)).isNull();
  }

  @ParameterizedTest
  @CsvSource({"idl,idl/method", ",method"})
  void unrecognizedMethodRetainsOriginalName(String serviceName, String originalMethod) {
    ThriftRequest request =
        new ThriftRequest("method", "handler", serviceName, null, null, emptyMap(), false);

    assertThat(getter.getMethod(request)).isEqualTo("method");
    assertThat(getter.getService(request)).isEqualTo("handler");
    assertThat(getter.getRpcMethod(request)).isEqualTo("_OTHER");
    assertThat(getter.getRpcMethodOriginal(request)).isEqualTo(originalMethod);
  }

  @Test
  void unrecognizedMethodWithoutOriginalName() {
    ThriftRequest request =
        new ThriftRequest(null, "handler", "idl", null, null, emptyMap(), false);

    assertThat(getter.getRpcMethod(request)).isEqualTo("_OTHER");
    assertThat(getter.getRpcMethodOriginal(request)).isNull();
  }

  @Test
  void originalNameIsOmittedWhenIdenticalToOther() {
    ThriftRequest request = new ThriftRequest("_OTHER", null, null, null, null, emptyMap(), false);

    assertThat(getter.getRpcMethod(request)).isEqualTo("_OTHER");
    assertThat(getter.getRpcMethodOriginal(request)).isNull();
  }

  @Test
  void nullSocketAddressesUseLegacyConstructor() {
    ThriftRequest request = new ThriftRequest("method", "service", null, null, emptyMap());

    assertThat(getter.getService(request)).isEqualTo("service");
    assertThat(getter.getRpcMethod(request)).isEqualTo("service/method");
    assertThat(request.getLocalAddress()).isNull();
    assertThat(request.getRemoteAddress()).isNull();
  }

  @ParameterizedTest
  @ValueSource(
      ints = {
        TApplicationException.UNKNOWN,
        TApplicationException.INTERNAL_ERROR,
        TApplicationException.UNSUPPORTED_CLIENT_TYPE
      })
  void applicationExceptionStatus(int code) {
    TApplicationException error = new TApplicationException(code);

    assertThat(getter.getRpcStatusCode(request, null, error)).isEqualTo(Integer.toString(code));
    // Preserve exception-class fallback for error.type.
    assertThat(getter.getErrorType(request, null, error)).isNull();
  }

  @ParameterizedTest
  @ValueSource(ints = {-1, 11, Integer.MAX_VALUE})
  void undefinedApplicationExceptionCode(int code) {
    TApplicationException error = new TApplicationException(code);

    assertThat(getter.getRpcStatusCode(request, null, error)).isNull();
    assertThat(getter.getErrorType(request, null, error)).isNull();
  }

  @ParameterizedTest
  @CsvSource({
    "0,UNKNOWN",
    "1,NOT_OPEN",
    "2,ALREADY_OPEN",
    "3,TIMED_OUT",
    "4,END_OF_FILE",
    "5,CORRUPTED_DATA",
    "6,6"
  })
  void transportErrorsAreNotRpcStatuses(int code, String errorType) {
    TTransportException error = new TTransportException(code);

    assertThat(getter.getRpcStatusCode(request, null, error)).isNull();
    assertThat(getter.getErrorType(request, null, error)).isEqualTo(errorType);
  }

  @Test
  void noStatusForSuccessOrFallbackErrors() {
    assertThat(getter.getRpcStatusCode(request, null, null)).isNull();
    assertThat(getter.getErrorType(request, null, null)).isNull();
    assertThat(getter.getRpcStatusCode(request, ThriftResponse.FAILED, null)).isNull();
    assertThat(getter.getErrorType(request, ThriftResponse.FAILED, null)).isEqualTo("_OTHER");
    assertThat(getter.getRpcStatusCode(request, null, new IllegalStateException())).isNull();
    assertThat(getter.getRpcStatusCode(request, null, new TProtocolException())).isNull();
  }
}
