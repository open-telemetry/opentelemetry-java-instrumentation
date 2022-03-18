/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.web;

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
import org.springframework.http.client.ClientHttpRequestInterceptor;

@ExtendWith(MockitoExtension.class)
class SpringWebTelemetryTest {
  @RegisterExtension
  static final LibraryInstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Mock HttpRequest httpRequestMock;
  @Mock ClientHttpRequestExecution requestExecutionMock;
  static final byte[] requestBody = new byte[0];

  @Test
  void shouldSkipWhenContextHasClientSpan() throws Exception {
    // given
    ClientHttpRequestInterceptor interceptor =
        SpringWebTelemetry.create(testing.getOpenTelemetry()).newInterceptor();

    // when
    testing.runWithClientSpan(
        "parent",
        () -> {
          interceptor.intercept(httpRequestMock, requestBody, requestExecutionMock);
        });

    // then
    then(requestExecutionMock).should().execute(httpRequestMock, requestBody);

    assertThat(testing.waitForTraces(1))
        .hasTracesSatisfyingExactly(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("parent").hasKind(SpanKind.CLIENT)));
  }
}
