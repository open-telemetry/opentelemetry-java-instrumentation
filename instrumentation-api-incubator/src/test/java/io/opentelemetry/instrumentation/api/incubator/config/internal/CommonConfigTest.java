/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.config.internal;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.api.incubator.log.LoggingContextConstants;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CommonConfigTest {

  @Test
  void capturedHeadersAreSnapshots() {
    ExtendedOpenTelemetry openTelemetry = mock(ExtendedOpenTelemetry.class);
    DeclarativeConfigProperties generalConfig =
        mock(DeclarativeConfigProperties.class, RETURNS_DEEP_STUBS);
    DeclarativeConfigProperties commonConfig =
        mock(DeclarativeConfigProperties.class, RETURNS_DEEP_STUBS);
    when(openTelemetry.getGeneralInstrumentationConfig()).thenReturn(generalConfig);
    when(openTelemetry.getInstrumentationConfig("common")).thenReturn(commonConfig);

    List<String> clientRequest = new ArrayList<>(asList("client-request"));
    List<String> clientResponse = new ArrayList<>(asList("client-response"));
    List<String> serverRequest = new ArrayList<>(asList("server-request"));
    List<String> serverResponse = new ArrayList<>();
    when(generalConfig
            .get("http")
            .get("client")
            .getScalarList(eq("request_captured_headers"), eq(String.class), anyList()))
        .thenReturn(clientRequest);
    when(generalConfig
            .get("http")
            .get("client")
            .getScalarList(eq("response_captured_headers"), eq(String.class), anyList()))
        .thenReturn(clientResponse);
    when(generalConfig
            .get("http")
            .get("server")
            .getScalarList(eq("request_captured_headers"), eq(String.class), anyList()))
        .thenReturn(serverRequest);
    when(generalConfig
            .get("http")
            .get("server")
            .getScalarList(eq("response_captured_headers"), eq(String.class), anyList()))
        .thenReturn(serverResponse);
    when(commonConfig
            .get("http")
            .getScalarList(eq("known_methods"), eq(String.class), anyList()))
        .thenReturn(new ArrayList<>());

    CommonConfig config = new CommonConfig(openTelemetry);
    clientRequest.add("later");
    clientResponse.add("later");
    serverRequest.add("later");

    assertThat(config.getClientRequestHeaders()).containsExactly("client-request");
    assertThat(config.getClientResponseHeaders()).containsExactly("client-response");
    assertThat(config.getServerRequestHeaders()).containsExactly("server-request");
    assertThat(config.getServerResponseHeaders()).isEmpty();
    assertThatThrownBy(() -> config.getClientRequestHeaders().add("other"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void loggingKeys() {
    ExtendedOpenTelemetry openTelemetry = mock(ExtendedOpenTelemetry.class);
    DeclarativeConfigProperties commonConfig =
        mock(DeclarativeConfigProperties.class, RETURNS_DEEP_STUBS);
    when(openTelemetry.getGeneralInstrumentationConfig())
        .thenReturn(DeclarativeConfigProperties.empty());
    when(openTelemetry.getInstrumentationConfig("common")).thenReturn(commonConfig);
    when(commonConfig.get("http").getScalarList(eq("known_methods"), eq(String.class), anyList()))
        .thenReturn(new ArrayList<>());
    DeclarativeConfigProperties logging = commonConfig.get("logging");
    when(logging.getString("trace_id_key", LoggingContextConstants.TRACE_ID))
        .thenReturn(LoggingContextConstants.TRACE_ID);
    when(logging.getString("span_id_key", LoggingContextConstants.SPAN_ID))
        .thenReturn(LoggingContextConstants.SPAN_ID);
    when(logging.getString("trace_flags_key", LoggingContextConstants.TRACE_FLAGS))
        .thenReturn(LoggingContextConstants.TRACE_FLAGS);

    CommonConfig config = new CommonConfig(openTelemetry);

    assertThat(config.getTraceIdKey()).isEqualTo(LoggingContextConstants.TRACE_ID);
    assertThat(config.getSpanIdKey()).isEqualTo(LoggingContextConstants.SPAN_ID);
    assertThat(config.getTraceFlagsKey()).isEqualTo(LoggingContextConstants.TRACE_FLAGS);

    when(logging.getString("trace_id_key", LoggingContextConstants.TRACE_ID))
        .thenReturn("supported_trace_id");
    when(logging.getString("span_id_key", LoggingContextConstants.SPAN_ID))
        .thenReturn("supported_span_id");
    when(logging.getString("trace_flags_key", LoggingContextConstants.TRACE_FLAGS))
        .thenReturn("supported_trace_flags");

    config = new CommonConfig(openTelemetry);

    assertThat(config.getTraceIdKey()).isEqualTo("supported_trace_id");
    assertThat(config.getSpanIdKey()).isEqualTo("supported_span_id");
    assertThat(config.getTraceFlagsKey()).isEqualTo("supported_trace_flags");
  }
}
