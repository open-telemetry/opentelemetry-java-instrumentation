/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactornetty.v1_0;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import java.net.InetSocketAddress;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.netty.http.client.HttpClientConfig;
import reactor.netty.http.client.HttpClientRequest;

@ExtendWith(MockitoExtension.class)
class FailedRequestWithUrlMakerTest {

  @Mock HttpClientConfig config;
  @Mock HttpClientRequest originalRequest;

  @Test
  void shouldUseAbsoluteUri() {
    when(config.uri()).thenReturn("https://opentelemetry.io");

    HttpClientRequest request = FailedRequestWithUrlMaker.create(config, originalRequest);

    assertThat(request.resourceUrl()).isEqualTo("https://opentelemetry.io");
  }

  @ParameterizedTest
  @ValueSource(strings = {"https://opentelemetry.io", "https://opentelemetry.io/"})
  void shouldPrependBaseUrl(String baseUrl) {
    when(config.baseUrl()).thenReturn(baseUrl);
    when(config.uri()).thenReturn("/docs");

    HttpClientRequest request = FailedRequestWithUrlMaker.create(config, originalRequest);

    assertThat(request.resourceUrl()).isEqualTo("https://opentelemetry.io/docs");
  }

  @Test
  @SuppressWarnings("MockitoDoSetup")
  void shouldPrependRemoteAddress() {
    when(config.baseUrl()).thenReturn("/");
    when(config.uri()).thenReturn("/docs");
    Supplier<InetSocketAddress> remoteAddress =
        () -> InetSocketAddress.createUnresolved("opentelemetry.io", 8080);
    doReturn(remoteAddress).when(config).remoteAddress();
    when(config.isSecure()).thenReturn(true);

    HttpClientRequest request = FailedRequestWithUrlMaker.create(config, originalRequest);

    assertThat(request.resourceUrl()).isEqualTo("https://opentelemetry.io:8080/docs");
  }

  @ParameterizedTest
  @CsvSource({
    "80, false",
    "443, true",
  })
  @SuppressWarnings("MockitoDoSetup")
  void shouldSkipDefaultPorts(int port, boolean isSecure) {
    when(config.baseUrl()).thenReturn("/");
    when(config.uri()).thenReturn("/docs");
    Supplier<InetSocketAddress> remoteAddress =
        () -> InetSocketAddress.createUnresolved("opentelemetry.io", port);
    doReturn(remoteAddress).when(config).remoteAddress();
    when(config.isSecure()).thenReturn(isSecure);

    HttpClientRequest request = FailedRequestWithUrlMaker.create(config, originalRequest);

    assertThat(request.resourceUrl())
        .isEqualTo((isSecure ? "https" : "http") + "://opentelemetry.io/docs");
  }
}
