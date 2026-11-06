/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v2_7;

import static io.opentelemetry.instrumentation.testing.util.TestLatestDeps.testLatestDeps;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusBuilder;
import java.lang.reflect.Method;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcInvocation;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.mockito.MockMakers;

class DubboRpcStatusTest {

  @Test
  void extractsStatusFromExceptionSubclassForClientAndServer() throws ReflectiveOperationException {
    Assumptions.assumeTrue(testLatestDeps(), "StatusRpcException requires Dubbo 3.x");

    Class<?> exceptionClass = Class.forName("org.apache.dubbo.rpc.StatusRpcException");
    Throwable exception =
        mock(
            exceptionClass.asSubclass(Throwable.class),
            withSettings().mockMaker(MockMakers.SUBCLASS));
    assertThat(exception.getClass()).isNotEqualTo(exceptionClass);

    Class<?> codeClass = Class.forName("org.apache.dubbo.rpc.TriRpcStatus$Code");
    @SuppressWarnings({"rawtypes", "unchecked"})
    Object code = Enum.valueOf((Class) codeClass, "INVALID_ARGUMENT");
    Class<?> statusClass = Class.forName("org.apache.dubbo.rpc.TriRpcStatus");
    Object status = statusClass.getMethod("fromCode", codeClass).invoke(null, code);
    Method getStatus = exceptionClass.getMethod("getStatus");
    when(getStatus.invoke(exception)).thenReturn(status);

    assertThat(DubboRpcStatus.getStatusCode(null, exception)).isEqualTo("INVALID_ARGUMENT");

    DubboRequest request =
        DubboRequest.createForUnknownService(mock(RpcInvocation.class), "service/method", null);
    Result response = null;
    for (boolean isClient : new boolean[] {true, false}) {
      String expectedErrorType = isClient ? "INVALID_ARGUMENT" : null;
      assertThat(new DubboRpcAttributesGetter(isClient).getErrorType(request, response, exception))
          .isEqualTo(expectedErrorType);

      SpanStatusBuilder spanStatusBuilder = mock(SpanStatusBuilder.class);
      new DubboRpcSpanStatusExtractor(isClient)
          .extract(spanStatusBuilder, request, response, exception);
      verify(spanStatusBuilder).setStatus(isClient ? StatusCode.ERROR : StatusCode.UNSET);
    }
  }
}
