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
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import java.util.function.Function;
import org.elasticsearch.client.Response;

public class ElasticsearchRestJavaagentInstrumenterFactory {

  // semconv says db.query.text "Should be collected by default for search-type queries and only if
  // there is sanitization that excludes sensitive information", which the sanitizer now provides.
  // The default flips only under v3-preview so that existing 2.x deployments keep their span shape.
  private static final boolean CAPTURE_SEARCH_QUERY =
      DeclarativeConfigUtil.getInstrumentationConfig(GlobalOpenTelemetry.get(), "elasticsearch")
          .getBoolean("capture_search_query", AgentCommonConfig.get().isV3Preview());

  private static final boolean SANITIZE_SEARCH_QUERY =
      DbConfig.isQuerySanitizationEnabled(GlobalOpenTelemetry.get(), "elasticsearch");

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
        SANITIZE_SEARCH_QUERY);
  }

  private ElasticsearchRestJavaagentInstrumenterFactory() {}
}
