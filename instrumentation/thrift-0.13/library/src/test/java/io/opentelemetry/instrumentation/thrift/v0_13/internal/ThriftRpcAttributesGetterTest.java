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
    ThriftRequest request = new ThriftRequest("method", "handler", "idl", null, emptyMap());

    assertThat(getter.getService(request)).isEqualTo("handler");
    assertThat(getter.getRpcMethod(request)).isEqualTo("idl/method");
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
    assertThat(getter.getErrorType(request, null, error))
        .isEqualTo(TApplicationException.class.getName());
  }

  @ParameterizedTest
  @ValueSource(ints = {-1, 11, Integer.MAX_VALUE})
  void undefinedApplicationExceptionCode(int code) {
    TApplicationException error = new TApplicationException(code);

    assertThat(getter.getRpcStatusCode(request, null, error)).isNull();
    assertThat(getter.getErrorType(request, null, error))
        .isEqualTo(TApplicationException.class.getName());
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
    IllegalStateException error = new IllegalStateException();
    assertThat(getter.getRpcStatusCode(request, null, error)).isNull();
    assertThat(getter.getErrorType(request, null, error))
        .isEqualTo(IllegalStateException.class.getName());
    TProtocolException protocolError = new TProtocolException();
    assertThat(getter.getRpcStatusCode(request, null, protocolError)).isNull();
    assertThat(getter.getErrorType(request, null, protocolError))
        .isEqualTo(TProtocolException.class.getName());
  }
}
