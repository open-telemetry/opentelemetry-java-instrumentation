/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.config.internal;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.api.incubator.log.LoggingContextConstants;
import io.opentelemetry.instrumentation.api.internal.HttpConstants;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class CommonConfig {

  private final List<String> clientRequestHeaders;
  private final List<String> clientResponseHeaders;
  private final List<String> serverRequestHeaders;
  private final List<String> serverResponseHeaders;
  private final Set<String> knownHttpRequestMethods;
  private final EnduserConfig enduserConfig;
  private final boolean emitExperimentalHttpClientTelemetry;
  private final boolean emitExperimentalHttpServerTelemetry;
  private final Set<String> sensitiveQueryParameters;
  private final String loggingTraceIdKey;
  private final String loggingSpanIdKey;
  private final String loggingTraceFlagsKey;
  private final boolean v3Preview;

  public CommonConfig(OpenTelemetry openTelemetry) {
    DeclarativeConfigProperties generalConfig =
        DeclarativeConfigUtil.getGeneralInstrumentationConfig(openTelemetry);
    DeclarativeConfigProperties commonConfig =
        DeclarativeConfigUtil.getInstrumentationConfig(openTelemetry, "common");

    clientRequestHeaders =
        generalConfig
            .get("http")
            .get("client")
            .getScalarList("request_captured_headers", String.class, new ArrayList<>());
    clientResponseHeaders =
        generalConfig
            .get("http")
            .get("client")
            .getScalarList("response_captured_headers", String.class, new ArrayList<>());
    serverRequestHeaders =
        generalConfig
            .get("http")
            .get("server")
            .getScalarList("request_captured_headers", String.class, new ArrayList<>());
    serverResponseHeaders =
        generalConfig
            .get("http")
            .get("server")
            .getScalarList("response_captured_headers", String.class, new ArrayList<>());
    knownHttpRequestMethods =
        new HashSet<>(
            commonConfig
                .get("http")
                .getScalarList(
                    "known_methods", String.class, new ArrayList<>(HttpConstants.KNOWN_METHODS)));
    emitExperimentalHttpClientTelemetry =
        commonConfig
            .get("http")
            .get("client")
            .getBoolean("emit_experimental_telemetry/development", false);

    List<String> sensitiveQueryParameterList =
        generalConfig
            .get("sanitization")
            .get("url")
            .getScalarList("sensitive_query_parameters/development", String.class);
    sensitiveQueryParameters =
        sensitiveQueryParameterList != null
            ? new HashSet<>(sensitiveQueryParameterList)
            : HttpConstants.SENSITIVE_QUERY_PARAMETERS;

    emitExperimentalHttpServerTelemetry =
        commonConfig
            .get("http")
            .get("server")
            .getBoolean("emit_experimental_telemetry/development", false);
    enduserConfig = new EnduserConfig(commonConfig);
    loggingTraceIdKey =
        commonConfig.get("logging").getString("trace_id", LoggingContextConstants.TRACE_ID);
    loggingSpanIdKey =
        commonConfig.get("logging").getString("span_id", LoggingContextConstants.SPAN_ID);
    loggingTraceFlagsKey =
        commonConfig.get("logging").getString("trace_flags", LoggingContextConstants.TRACE_FLAGS);
    v3Preview = commonConfig.getBoolean("v3_preview", false);
  }

  public List<String> getClientRequestHeaders() {
    return clientRequestHeaders;
  }

  public List<String> getClientResponseHeaders() {
    return clientResponseHeaders;
  }

  public List<String> getServerRequestHeaders() {
    return serverRequestHeaders;
  }

  public List<String> getServerResponseHeaders() {
    return serverResponseHeaders;
  }

  public Set<String> getKnownHttpRequestMethods() {
    return knownHttpRequestMethods;
  }

  public EnduserConfig getEnduserConfig() {
    return enduserConfig;
  }

  public boolean shouldEmitExperimentalHttpClientTelemetry() {
    return emitExperimentalHttpClientTelemetry;
  }

  public boolean shouldEmitExperimentalHttpServerTelemetry() {
    return emitExperimentalHttpServerTelemetry;
  }

  public Set<String> getSensitiveQueryParameters() {
    return sensitiveQueryParameters;
  }

  public String getTraceIdKey() {
    return loggingTraceIdKey;
  }

  public String getSpanIdKey() {
    return loggingSpanIdKey;
  }

  public String getTraceFlagsKey() {
    return loggingTraceFlagsKey;
  }

  public boolean isV3Preview() {
    return v3Preview;
  }
}
