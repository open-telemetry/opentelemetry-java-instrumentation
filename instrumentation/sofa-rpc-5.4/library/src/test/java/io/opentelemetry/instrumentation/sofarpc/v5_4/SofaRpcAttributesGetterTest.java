/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.sofarpc.v5_4;

import static org.assertj.core.api.Assertions.assertThat;

import com.alipay.sofa.rpc.common.RemotingConstants;
import com.alipay.sofa.rpc.core.exception.RpcErrorType;
import com.alipay.sofa.rpc.core.exception.SofaRpcException;
import com.alipay.sofa.rpc.core.request.SofaRequest;
import com.alipay.sofa.rpc.core.response.SofaResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SofaRpcAttributesGetterTest {

  private final SofaRpcAttributesGetter getter = new SofaRpcAttributesGetter();
  private final SofaRpcRequest request = SofaRpcRequest.create(new SofaRequest());

  @ParameterizedTest
  @ValueSource(ints = {100, 160, 200, 250, 299})
  void rpcErrorCode(int code) {
    SofaRpcException error = new SofaRpcException(code, "RPC error");

    assertThat(getter.getRpcStatusCode(request, null, error)).isEqualTo(Integer.toString(code));
    assertThat(getter.getErrorType(request, null, error)).isEqualTo(Integer.toString(code));
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 99, 300})
  void unavailableRpcCode(int code) {
    SofaRpcException error = new SofaRpcException(code, "RPC error");

    assertThat(getter.getRpcStatusCode(request, null, error)).isNull();
    assertThat(getter.getErrorType(request, null, error))
        .isEqualTo(SofaRpcException.class.getName());
  }

  @Test
  void errorTypeForNonSofaException() {
    SofaResponse response = new SofaResponse();
    assertThat(getter.getRpcStatusCode(request, response, null)).isNull();
    assertThat(getter.getErrorType(request, response, null)).isNull();

    IllegalStateException error = new IllegalStateException();
    assertThat(getter.getRpcStatusCode(request, response, error)).isNull();
    assertThat(getter.getErrorType(request, response, error))
        .isEqualTo(IllegalStateException.class.getName());
  }

  @Test
  void responseOnlyError() {
    SofaResponse response = new SofaResponse();
    response.addResponseProp(RemotingConstants.HEAD_RESPONSE_ERROR, "true");

    assertThat(getter.getRpcStatusCode(request, response, null)).isNull();
    assertThat(getter.getErrorType(request, response, null))
        .isEqualTo(Integer.toString(RpcErrorType.SERVER_UNDECLARED_ERROR));

    SofaRpcException error = new SofaRpcException(160, "RPC error");
    assertThat(getter.getRpcStatusCode(request, response, error)).isEqualTo("160");
    assertThat(getter.getErrorType(request, response, error)).isEqualTo("160");

    IllegalStateException applicationError = new IllegalStateException();
    assertThat(getter.getErrorType(request, response, applicationError))
        .isEqualTo(IllegalStateException.class.getName());
  }

  @Test
  void errorMessageIndicatesResponseError() {
    SofaResponse response = new SofaResponse();
    response.setErrorMsg("RPC error");

    assertThat(response.isError()).isTrue();
    assertThat(getter.getRpcStatusCode(request, response, null)).isNull();
    assertThat(getter.getErrorType(request, response, null))
        .isEqualTo(Integer.toString(RpcErrorType.SERVER_UNDECLARED_ERROR));
  }
}
