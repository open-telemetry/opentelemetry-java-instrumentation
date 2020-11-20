/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambda.v1_0;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import io.opentelemetry.context.propagation.DefaultContextPropagators;
import io.opentelemetry.extension.trace.propagation.AwsXRayPropagator;
import io.opentelemetry.extension.trace.propagation.B3Propagator;
import io.opentelemetry.instrumentation.test.AgentTestRunner;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

class ApiGatewayProxyRequestTest {

  @Test
  public void shouldCreateNoopRequestIfNoPropagatorsSet() throws IOException {
    // given
    InputStream mock = mock(InputStream.class);
    AgentTestRunner.setGlobalPropagators(DefaultContextPropagators.builder().build());
    // when
    ApiGatewayProxyRequest created = ApiGatewayProxyRequest.forStream(mock);
    // then
    assertThat(created.freshStream()).isEqualTo(mock);
    assertThat(created.getHeaders()).isNull();
  }

  @Test
  public void shouldCreateNoopRequestIfXRayPropagatorsSet() throws IOException {
    // given
    InputStream mock = mock(InputStream.class);
    AgentTestRunner.setGlobalPropagators(
        DefaultContextPropagators.builder()
            .addTextMapPropagator(AwsXRayPropagator.getInstance())
            .build());
    // when
    ApiGatewayProxyRequest created = ApiGatewayProxyRequest.forStream(mock);
    // then
    assertThat(created.freshStream()).isEqualTo(mock);
    assertThat(created.getHeaders()).isNull();
  }

  @Test
  public void shouldUseStreamMarkingIfHttpPropagatorsSet() throws IOException {
    // given
    InputStream mock = mock(InputStream.class);
    given(mock.markSupported()).willReturn(true);
    AgentTestRunner.setGlobalPropagators(
        DefaultContextPropagators.builder()
            .addTextMapPropagator(B3Propagator.getInstance())
            .build());
    // when
    ApiGatewayProxyRequest created = ApiGatewayProxyRequest.forStream(mock);
    // then
    assertThat(created.freshStream()).isEqualTo(mock);
    then(mock).should(atLeastOnce()).mark(Integer.MAX_VALUE);
    then(mock).should().reset();
  }

  @Test
  public void shouldUseCopyIfMarkingNotAvailableAndHttpPropagatorsSet() throws IOException {
    // given
    InputStream mock = mock(InputStream.class);
    given(mock.markSupported()).willReturn(false);
    given(mock.read(any(byte[].class))).willReturn(-1);

    AgentTestRunner.setGlobalPropagators(
        DefaultContextPropagators.builder()
            .addTextMapPropagator(B3Propagator.getInstance())
            .build());
    // when
    ApiGatewayProxyRequest created = ApiGatewayProxyRequest.forStream(mock);
    // then
    assertThat(created.freshStream()).isInstanceOf(ByteArrayInputStream.class);
    then(mock).should(never()).mark(any(Integer.class));
    then(mock).should(never()).reset();
    then(mock).should().read(any());
  }
}
