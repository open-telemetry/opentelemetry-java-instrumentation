/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdacore.v1_0.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.contrib.awsxray.propagator.AwsXrayPropagator;
import io.opentelemetry.extension.trace.propagation.B3Propagator;
import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ApiGatewayProxyRequestTest {

  @BeforeEach
  void resetOpenTelemetry() {
    GlobalOpenTelemetry.resetForTest();
  }

  @Test
  void shouldCreateNoopRequestIfNoPropagatorsSet() throws IOException {
    // given
    InputStream mock = mock(InputStream.class);
    GlobalOpenTelemetry.set(OpenTelemetry.noop());
    // when
    ApiGatewayProxyRequest created = ApiGatewayProxyRequest.forStream(mock);
    // then
    assertThat(created.freshStream()).isEqualTo(mock);
    assertThat(created.getHeaders()).isEmpty();
  }

  @Test
  void shouldCreateNoopRequestIfXrayPropagatorsSet() throws IOException {
    // given
    InputStream mock = mock(InputStream.class);
    GlobalOpenTelemetry.set(
        OpenTelemetry.propagating(ContextPropagators.create(AwsXrayPropagator.getInstance())));
    // when
    ApiGatewayProxyRequest created = ApiGatewayProxyRequest.forStream(mock);
    // then
    assertThat(created.freshStream()).isEqualTo(mock);
    assertThat(created.getHeaders()).isEmpty();
  }

  @Test
  void shouldUseStreamMarkingIfHttpPropagatorsSet() throws IOException {
    // given
    InputStream mock = mock(InputStream.class);
    when(mock.markSupported()).thenReturn(true);
    GlobalOpenTelemetry.set(
        OpenTelemetry.propagating(ContextPropagators.create(B3Propagator.injectingSingleHeader())));
    // when
    ApiGatewayProxyRequest created = ApiGatewayProxyRequest.forStream(mock);
    // then
    assertThat(created.freshStream()).isEqualTo(mock);
    verify(mock, atLeastOnce()).mark(Integer.MAX_VALUE);
    verify(mock).reset();
  }

  @Test
  void shouldUseNoopIfMarkingNotAvailableAndHttpPropagatorsSet() throws IOException {
    // given
    InputStream mock = mock(InputStream.class);
    when(mock.markSupported()).thenReturn(false);
    when(mock.read(any(byte[].class))).thenReturn(-1);
    GlobalOpenTelemetry.set(
        OpenTelemetry.propagating(ContextPropagators.create(B3Propagator.injectingSingleHeader())));
    // when
    ApiGatewayProxyRequest created = ApiGatewayProxyRequest.forStream(mock);
    // then
    assertThat(created.freshStream()).isEqualTo(mock);
    assertThat(created.getHeaders()).isEmpty();
  }
}
