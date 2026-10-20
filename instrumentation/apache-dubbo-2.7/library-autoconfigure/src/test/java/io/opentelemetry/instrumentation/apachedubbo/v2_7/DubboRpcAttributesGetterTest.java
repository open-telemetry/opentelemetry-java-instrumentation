/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v2_7;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CompletionException;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.RpcInvocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DubboRpcAttributesGetterTest {

  private final DubboRpcAttributesGetter getter = new DubboRpcAttributesGetter();
  private final DubboRequest request =
      DubboRequest.createForUnknownService(new RpcInvocation(), "_OTHER", null);

  @ParameterizedTest
  @ValueSource(ints = {1, 13})
  void definedCode(int code) {
    assertThat(getter.getErrorType(request, null, new RpcException(code)))
        .isEqualTo(Integer.toString(code));
  }

  @ParameterizedTest
  @ValueSource(ints = {-1, 0, 14, Integer.MAX_VALUE})
  void unknownCode(int code) {
    assertThat(getter.getErrorType(request, null, new RpcException(code))).isNull();
  }

  @Test
  void ordinaryException() {
    assertThat(getter.getErrorType(request, null, new IllegalStateException())).isNull();
  }

  @Test
  void unwrapsCompletionException() {
    assertThat(
            getter.getErrorType(
                request,
                null,
                new CompletionException(new CompletionException(new RpcException(1)))))
        .isEqualTo("1");
  }
}
