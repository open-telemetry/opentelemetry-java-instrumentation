/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.config.internal;

import static java.util.Collections.emptyList;
import static java.util.Collections.unmodifiableList;

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
  private final UserConfig userConfig;
  private final boolean emitExperimentalHttpClientTelemetry;
  private final boolean emitExperimentalHttpServerTelemetry;
  private final Set<String> sensitiveQueryParameters;
  private final String loggingTraceIdKey;
  private final String loggingSpanIdKey;
  private final String loggingTraceFlagsKey;

  public CommonConfig(OpenTelemetry openTelemetry) {
    DeclarativeConfigProperties generalConfig =
        DeclarativeConfigUtil.getGeneralInstrumentationConfig(openTelemetry);
    DeclarativeConfigProperties httpConfig = generalConfig.get("http");
    DeclarativeConfigProperties clientConfig = httpConfig.get("client");
    DeclarativeConfigProperties serverConfig = httpConfig.get("server");
    DeclarativeConfigProperties commonConfig =
        DeclarativeConfigUtil.getInstrumentationConfig(openTelemetry, "common");

    clientRequestHeaders = readCapturedHeaders(clientConfig, "request_captured_headers");
    clientResponseHeaders = readCapturedHeaders(clientConfig, "response_captured_headers");
    serverRequestHeaders = readCapturedHeaders(serverConfig, "request_captured_headers");
    serverResponseHeaders = readCapturedHeaders(serverConfig, "response_captured_headers");
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
            .getScalarList("sensitive_query_parameters", String.class);
    sensitiveQueryParameters =
        sensitiveQueryParameterList != null
            ? new HashSet<>(sensitiveQueryParameterList)
            : HttpConstants.SENSITIVE_QUERY_PARAMETERS;

    emitExperimentalHttpServerTelemetry =
        commonConfig
            .get("http")
            .get("server")
            .getBoolean("emit_experimental_telemetry/development", false);
    userConfig = new UserConfig(commonConfig);
    DeclarativeConfigProperties logging = commonConfig.get("logging");
    loggingTraceIdKey = logging.getString("trace_id_key", LoggingContextConstants.TRACE_ID);
    loggingSpanIdKey = logging.getString("span_id_key", LoggingContextConstants.SPAN_ID);
    loggingTraceFlagsKey =
        logging.getString("trace_flags_key", LoggingContextConstants.TRACE_FLAGS);
  }

  private static List<String> readCapturedHeaders(DeclarativeConfigProperties config, String key) {
    return unmodifiableList(new ArrayList<>(config.getScalarList(key, String.class, emptyList())));
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

  public UserConfig getUserConfig() {
    return userConfig;
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
}
