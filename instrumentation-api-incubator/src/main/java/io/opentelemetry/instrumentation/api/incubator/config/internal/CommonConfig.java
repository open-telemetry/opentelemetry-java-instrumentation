/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.config.internal;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.incubator.config.ConfigProvider;
import io.opentelemetry.instrumentation.api.incubator.log.LoggingContextConstants;
import io.opentelemetry.instrumentation.api.incubator.semconv.net.PeerServiceResolver;
import io.opentelemetry.instrumentation.api.internal.HttpConstants;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class CommonConfig {

  private final PeerServiceResolver peerServiceResolver;
  private final List<String> clientRequestHeaders;
  private final List<String> clientResponseHeaders;
  private final List<String> serverRequestHeaders;
  private final List<String> serverResponseHeaders;
  private final Set<String> knownHttpRequestMethods;
  private final EnduserConfig enduserConfig;
  private final boolean statementSanitizationEnabled;
  private final boolean sqlCommenterEnabled;
  private final boolean emitExperimentalHttpClientTelemetry;
  private final boolean emitExperimentalHttpServerTelemetry;
  private final boolean redactQueryParameters;
  private final String loggingTraceIdKey;
  private final String loggingSpanIdKey;
  private final String loggingTraceFlagsKey;

  interface ValueProvider<T> {
    @Nullable
    T get(ConfigProvider configProvider);
  }

  public CommonConfig(OpenTelemetry openTelemetry) {
    ExtendedDeclarativeConfigProperties generalConfig =
        DeclarativeConfigUtil.getGeneralInstrumentationConfig(openTelemetry);
    ExtendedDeclarativeConfigProperties commonConfig =
        DeclarativeConfigUtil.getInstrumentationConfig(openTelemetry, "common");
    peerServiceResolver = PeerServiceResolver.create(openTelemetry);

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
    statementSanitizationEnabled =
        commonConfig.get("database").get("statement_sanitizer").getBoolean("enabled", true);
    sqlCommenterEnabled =
        commonConfig.get("database").get("sqlcommenter/development").getBoolean("enabled", false);
    emitExperimentalHttpClientTelemetry =
        commonConfig
            .get("http")
            .get("client")
            .getBoolean("emit_experimental_telemetry/development", false);
    redactQueryParameters =
        commonConfig
            .get("http")
            .get("client")
            .getBoolean("redact_query_parameters/development", true);
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
  }

  public PeerServiceResolver getPeerServiceResolver() {
    return peerServiceResolver;
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

  public boolean isStatementSanitizationEnabled() {
    return statementSanitizationEnabled;
  }

  public boolean isSqlCommenterEnabled() {
    return sqlCommenterEnabled;
  }

  public boolean shouldEmitExperimentalHttpClientTelemetry() {
    return emitExperimentalHttpClientTelemetry;
  }

  public boolean shouldEmitExperimentalHttpServerTelemetry() {
    return emitExperimentalHttpServerTelemetry;
  }

  public boolean redactQueryParameters() {
    return redactQueryParameters;
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
