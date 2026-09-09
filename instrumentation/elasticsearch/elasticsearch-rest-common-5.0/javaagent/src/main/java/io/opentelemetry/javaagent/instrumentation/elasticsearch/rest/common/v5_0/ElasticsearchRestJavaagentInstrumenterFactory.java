/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.rest.common.v5_0;

import static java.util.Collections.emptyList;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DbConfig;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.elasticsearch.rest.common.v5_0.internal.ElasticsearchRestInstrumenterFactory;
import io.opentelemetry.instrumentation.elasticsearch.rest.common.v5_0.internal.ElasticsearchRestRequest;
import io.opentelemetry.javaagent.bootstrap.elasticsearch.ElasticsearchQuerySanitizerAccess;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import javax.annotation.Nullable;
import org.elasticsearch.client.Response;

public class ElasticsearchRestJavaagentInstrumenterFactory {

  private static final boolean CAPTURE_SEARCH_QUERY =
      ElasticsearchRestConfig.captureSearchQuery(
          DeclarativeConfigUtil.getInstrumentationConfig(
              GlobalOpenTelemetry.get(), "elasticsearch"),
          AgentCommonConfig.get().isV3Preview());

  private static final boolean SANITIZE_SEARCH_QUERY =
      DbConfig.isQuerySanitizationEnabled(GlobalOpenTelemetry.get(), "elasticsearch");

  // the sanitizer needs a JSON parser, which only exists in the agent class loader, so it is
  // reached through a bootstrap class rather than referenced directly from this injected helper
  @Nullable
  private static final UnaryOperator<String> sanitizer =
      SANITIZE_SEARCH_QUERY ? ElasticsearchQuerySanitizerAccess::sanitize : null;

  public static Instrumenter<ElasticsearchRestRequest, Response> create(
      String instrumentationName) {
    return ElasticsearchRestInstrumenterFactory.create(
        GlobalOpenTelemetry.get(),
        instrumentationName,
        emptyList(),
        Function.identity(),
        AgentCommonConfig.get().getKnownHttpRequestMethods(),
        AgentCommonConfig.get().getSensitiveQueryParameters(),
        CAPTURE_SEARCH_QUERY,
        sanitizer);
  }

  private ElasticsearchRestJavaagentInstrumenterFactory() {}
}
