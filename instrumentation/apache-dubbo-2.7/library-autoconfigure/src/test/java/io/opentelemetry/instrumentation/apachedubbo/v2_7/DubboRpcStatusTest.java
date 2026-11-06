/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v2_7;

import static io.opentelemetry.instrumentation.testing.util.TestLatestDeps.testLatestDeps;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusBuilder;
import java.lang.reflect.Method;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcInvocation;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockMakers;

class DubboRpcStatusTest {

  @ParameterizedTest
  @ValueSource(strings = {"INVALID_ARGUMENT", "INTERNAL"})
  void extractsStatusFromExceptionSubclassForClientAndServer(String statusCode)
      throws ReflectiveOperationException {
    Assumptions.assumeTrue(testLatestDeps(), "StatusRpcException requires Dubbo 3.x");

    Class<?> exceptionClass = Class.forName("org.apache.dubbo.rpc.StatusRpcException");
    Throwable exception =
        mock(
            exceptionClass.asSubclass(Throwable.class),
            withSettings().mockMaker(MockMakers.SUBCLASS));
    assertThat(exception.getClass()).isNotEqualTo(exceptionClass);

    Class<?> codeClass = Class.forName("org.apache.dubbo.rpc.TriRpcStatus$Code");
    @SuppressWarnings({"rawtypes", "unchecked"})
    Object code = Enum.valueOf((Class) codeClass, statusCode);
    Class<?> statusClass = Class.forName("org.apache.dubbo.rpc.TriRpcStatus");
    Object status = statusClass.getMethod("fromCode", codeClass).invoke(null, code);
    Method getStatus = exceptionClass.getMethod("getStatus");
    when(getStatus.invoke(exception)).thenReturn(status);

    assertThat(DubboRpcStatus.getStatusCode(null, exception)).isEqualTo(statusCode);

    DubboRequest request =
        DubboRequest.createForUnknownService(
            mock(RpcInvocation.class, withSettings().mockMaker(MockMakers.SUBCLASS)),
            "service/method",
            null);
    Result response = null;
    for (boolean isClient : new boolean[] {true, false}) {
      assertThat(new DubboRpcAttributesGetter(isClient).getErrorType(request, response, exception))
          .isEqualTo(isClient || "INTERNAL".equals(statusCode) ? statusCode : null);

      SpanStatusBuilder spanStatusBuilder =
          mock(SpanStatusBuilder.class, withSettings().mockMaker(MockMakers.SUBCLASS));
      new DubboRpcSpanStatusExtractor(isClient)
          .extract(spanStatusBuilder, request, response, exception);
      if (isClient || "INTERNAL".equals(statusCode)) {
        verify(spanStatusBuilder).setStatus(StatusCode.ERROR);
      } else {
        verifyNoInteractions(spanStatusBuilder);
      }
    }
  }
}
