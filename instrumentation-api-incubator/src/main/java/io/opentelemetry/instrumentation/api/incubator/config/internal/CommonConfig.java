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
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class CommonConfig {
  private static final Logger logger = Logger.getLogger(CommonConfig.class.getName());
  private static final Set<String> warnedDeprecatedProperties = ConcurrentHashMap.newKeySet();

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
    v3Preview = commonConfig.getBoolean("v3_preview", false);
    userConfig = new UserConfig(commonConfig, v3Preview);
    DeclarativeConfigProperties logging = commonConfig.get("logging");
    loggingTraceIdKey =
        getConfig(
            logging,
            "trace_id_key",
            "trace_id",
            "otel.instrumentation.common.logging.trace-id-key",
            "otel.instrumentation.common.logging.trace-id",
            LoggingContextConstants.TRACE_ID);
    loggingSpanIdKey =
        getConfig(
            logging,
            "span_id_key",
            "span_id",
            "otel.instrumentation.common.logging.span-id-key",
            "otel.instrumentation.common.logging.span-id",
            LoggingContextConstants.SPAN_ID);
    loggingTraceFlagsKey =
        getConfig(
            logging,
            "trace_flags_key",
            "trace_flags",
            "otel.instrumentation.common.logging.trace-flags-key",
            "otel.instrumentation.common.logging.trace-flags",
            LoggingContextConstants.TRACE_FLAGS);
  }

  private static String getConfig(
      DeclarativeConfigProperties config,
      String newDeclarativeKey,
      String oldDeclarativeKey,
      String newProperty,
      String oldProperty,
      String defaultValue) {
    String value = config.getString(newDeclarativeKey);
    if (value != null) {
      return value;
    }
    value = config.getString(oldDeclarativeKey);
    if (value != null) {
      if (warnedDeprecatedProperties.add(oldProperty)) {
        logger.warning(
            "The "
                + oldProperty
                + " setting and the equivalent declarative configuration property"
                + " are deprecated and will be removed in 3.0. Use "
                + newProperty
                + " or equivalent declarative configuration instead.");
      }
      return value;
    }
    return defaultValue;
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

  public boolean isV3Preview() {
    return v3Preview;
  }
}
