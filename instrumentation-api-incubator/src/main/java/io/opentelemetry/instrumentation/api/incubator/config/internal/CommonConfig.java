/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.config.internal;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;

import io.opentelemetry.api.incubator.config.ConfigProvider;
import io.opentelemetry.api.incubator.config.InstrumentationConfigUtil;
import io.opentelemetry.instrumentation.api.incubator.log.LoggingContextConstants;
import io.opentelemetry.instrumentation.api.incubator.semconv.net.PeerServiceResolver;
import io.opentelemetry.instrumentation.api.internal.HttpConstants;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

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
  private final boolean emitExperimentalHttpClientTelemetry;
  private final boolean emitExperimentalHttpServerTelemetry;
  private final boolean redactQueryParameters;
  private final String loggingTraceIdKey;
  private final String loggingSpanIdKey;
  private final String loggingTraceFlagsKey;

  public CommonConfig(InstrumentationConfig config) {
    peerServiceResolver =
        PeerServiceResolver.create(
            getFromConfigProviderOrFallback(
                config,
                InstrumentationConfigUtil::peerServiceMapping,
                emptyMap(),
                () ->
                    config.getMap("otel.instrumentation.common.peer-service-mapping", emptyMap())));

    clientRequestHeaders =
        getFromConfigProviderOrFallback(
            config,
            InstrumentationConfigUtil::httpClientRequestCapturedHeaders,
            emptyList(),
            () -> config.getList("otel.instrumentation.http.client.capture-request-headers"));
    clientResponseHeaders =
        getFromConfigProviderOrFallback(
            config,
            InstrumentationConfigUtil::httpClientResponseCapturedHeaders,
            emptyList(),
            () -> config.getList("otel.instrumentation.http.client.capture-response-headers"));
    serverRequestHeaders =
        getFromConfigProviderOrFallback(
            config,
            InstrumentationConfigUtil::httpServerRequestCapturedHeaders,
            emptyList(),
            () -> config.getList("otel.instrumentation.http.server.capture-request-headers"));
    serverResponseHeaders =
        getFromConfigProviderOrFallback(
            config,
            InstrumentationConfigUtil::httpServerResponseCapturedHeaders,
            emptyList(),
            () -> config.getList("otel.instrumentation.http.server.capture-response-headers"));
    knownHttpRequestMethods =
        new HashSet<>(
            config.getList(
                "otel.instrumentation.http.known-methods",
                new ArrayList<>(HttpConstants.KNOWN_METHODS)));
    statementSanitizationEnabled =
        config.getBoolean("otel.instrumentation.common.db-statement-sanitizer.enabled", true);
    emitExperimentalHttpClientTelemetry =
        config.getBoolean("otel.instrumentation.http.client.emit-experimental-telemetry", false);
    redactQueryParameters =
        config.getBoolean(
            "otel.instrumentation.http.client.experimental.redact-query-parameters", true);
    emitExperimentalHttpServerTelemetry =
        config.getBoolean("otel.instrumentation.http.server.emit-experimental-telemetry", false);
    enduserConfig = new EnduserConfig(config);
    loggingTraceIdKey =
        config.getString(
            "otel.instrumentation.common.logging.trace-id", LoggingContextConstants.TRACE_ID);
    loggingSpanIdKey =
        config.getString(
            "otel.instrumentation.common.logging.span-id", LoggingContextConstants.SPAN_ID);
    loggingTraceFlagsKey =
        config.getString(
            "otel.instrumentation.common.logging.trace-flags", LoggingContextConstants.TRACE_FLAGS);
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

  private static <T> T getFromConfigProviderOrFallback(
      InstrumentationConfig config,
      Function<ConfigProvider, T> getFromConfigProvider,
      T defaultValue,
      Supplier<T> fallback) {
    ConfigProvider configProvider = config.getConfigProvider();
    if (configProvider != null) {
      T value = getFromConfigProvider.apply(configProvider);
      return value != null ? value : defaultValue;
    }
    // fallback doesn't return null, so we can safely call it
    return fallback.get();
  }
}
