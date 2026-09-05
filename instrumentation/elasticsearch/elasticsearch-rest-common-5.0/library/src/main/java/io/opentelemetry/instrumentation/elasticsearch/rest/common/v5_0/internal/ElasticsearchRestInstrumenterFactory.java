/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.elasticsearch.rest.common.v5_0.internal;

import static io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbExceptionEventExtractors.setDbClientExceptionEventExtractor;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import javax.annotation.Nullable;
import org.elasticsearch.client.Response;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 *
 * @deprecated The Elasticsearch REST library instrumentation is deprecated. Use the Elasticsearch
 *     Java API Client's native OpenTelemetry support when migrating clients, or use the javaagent
 *     for direct REST client instrumentation. May be removed in the next minor release.
 */
@Deprecated // may be removed in the next minor release
@SuppressWarnings("deprecation")
public final class ElasticsearchRestInstrumenterFactory {

  public static Instrumenter<ElasticsearchRestRequest, Response> create(
      OpenTelemetry openTelemetry,
      String instrumentationName,
      List<AttributesExtractor<ElasticsearchRestRequest, Response>> attributesExtractors,
      Function<
              SpanNameExtractor<ElasticsearchRestRequest>,
              ? extends SpanNameExtractor<? super ElasticsearchRestRequest>>
          spanNameExtractorTransformer,
      Set<String> knownMethods,
      Set<String> sensitiveQueryParameters,
      boolean captureSearchQuery,
      @Nullable UnaryOperator<String> sanitizer) {
    ElasticsearchDbAttributesGetter dbClientAttributesGetter =
        new ElasticsearchDbAttributesGetter(captureSearchQuery, sanitizer);
    ElasticsearchClientAttributeExtractor esClientAttributesExtractor =
        new ElasticsearchClientAttributeExtractor(knownMethods, sensitiveQueryParameters);
    SpanNameExtractor<? super ElasticsearchRestRequest> spanNameExtractor =
        spanNameExtractorTransformer.apply(
            new ElasticsearchSpanNameExtractor(dbClientAttributesGetter));

    InstrumenterBuilder<ElasticsearchRestRequest, Response> builder =
        Instrumenter.<ElasticsearchRestRequest, Response>builder(
                openTelemetry, instrumentationName, spanNameExtractor)
            .addAttributesExtractor(DbClientAttributesExtractor.create(dbClientAttributesGetter))
            .addAttributesExtractor(esClientAttributesExtractor)
            .addAttributesExtractors(attributesExtractors)
            .addOperationMetrics(DbClientMetrics.get());
    setDbClientExceptionEventExtractor(builder);
    return builder.buildInstrumenter(SpanKindExtractor.alwaysClient());
  }

  private ElasticsearchRestInstrumenterFactory() {}
}
