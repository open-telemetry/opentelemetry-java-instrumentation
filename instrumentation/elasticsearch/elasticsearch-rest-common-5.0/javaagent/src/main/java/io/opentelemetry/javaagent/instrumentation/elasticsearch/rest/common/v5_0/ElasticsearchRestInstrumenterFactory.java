/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.rest.common.v5_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DbConfig;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbExceptionEventExtractors;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.javaagent.bootstrap.elasticsearch.ElasticsearchQuerySanitizerAccess;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import java.util.function.UnaryOperator;
import javax.annotation.Nullable;
import org.elasticsearch.client.Response;

public final class ElasticsearchRestInstrumenterFactory {

  private static final boolean CAPTURE_SEARCH_QUERY =
      captureSearchQuery(
          DeclarativeConfigUtil.getInstrumentationConfig(
              GlobalOpenTelemetry.get(), "elasticsearch"));

  private static final boolean SANITIZE_SEARCH_QUERY =
      DbConfig.isQuerySanitizationEnabled(GlobalOpenTelemetry.get(), "elasticsearch");

  // the sanitizer needs a JSON parser, which only exists in the agent class loader, so it is
  // reached through a bootstrap class rather than referenced directly from this injected helper
  @Nullable
  private static final UnaryOperator<String> sanitizer =
      SANITIZE_SEARCH_QUERY ? ElasticsearchQuerySanitizerAccess::sanitize : null;

  public static Instrumenter<ElasticsearchRestRequest, Response> create(
      String instrumentationName) {
    ElasticsearchDbAttributesGetter dbClientAttributesGetter =
        new ElasticsearchDbAttributesGetter(CAPTURE_SEARCH_QUERY, sanitizer);
    ElasticsearchClientAttributeExtractor esClientAttributesExtractor =
        new ElasticsearchClientAttributeExtractor(
            AgentCommonConfig.get().getKnownHttpRequestMethods(),
            AgentCommonConfig.get().getSensitiveQueryParameters());
    InstrumenterBuilder<ElasticsearchRestRequest, Response> builder =
        Instrumenter.<ElasticsearchRestRequest, Response>builder(
                GlobalOpenTelemetry.get(),
                instrumentationName,
                new ElasticsearchSpanNameExtractor(dbClientAttributesGetter))
            .addAttributesExtractor(DbClientAttributesExtractor.create(dbClientAttributesGetter))
            .addAttributesExtractor(esClientAttributesExtractor)
            .addOperationMetrics(DbClientMetrics.get());
    DbExceptionEventExtractors.setDbClientExceptionEventExtractor(builder);
    return builder.buildInstrumenter(SpanKindExtractor.alwaysClient());
  }

  private static boolean captureSearchQuery(DeclarativeConfigProperties config) {
    return ElasticsearchRestConfig.captureSearchQuery(
        config, AgentCommonConfig.get().isV3Preview());
  }

  private ElasticsearchRestInstrumenterFactory() {}
}
