/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.httpclients;

import static io.opentelemetry.instrumentation.testing.util.TraceUtils.withClientSpan;
import static io.opentelemetry.sdk.testing.assertj.TracesAssert.assertThat;
import static org.mockito.BDDMockito.then;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;

@ExtendWith(MockitoExtension.class)
class RestTemplateInterceptorTest {
  @RegisterExtension
  static final LibraryInstrumentationExtension instrumentation =
      LibraryInstrumentationExtension.create();

  @Mock HttpRequest httpRequestMock;
  @Mock ClientHttpRequestExecution requestExecutionMock;
  byte[] requestBody = new byte[0];

  @Test
  void shouldSkipWhenContextHasClientSpan() throws Exception {
    // given
    RestTemplateInterceptor interceptor =
        new RestTemplateInterceptor(instrumentation.getOpenTelemetry());

    // when
    withClientSpan(
        "parent",
        () -> {
          interceptor.intercept(httpRequestMock, requestBody, requestExecutionMock);
        });

    // then
    then(requestExecutionMock).should().execute(httpRequestMock, requestBody);

    assertThat(instrumentation.waitForTraces(1))
        .hasTracesSatisfyingExactly(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("parent").hasKind(SpanKind.CLIENT)));
  }
}
