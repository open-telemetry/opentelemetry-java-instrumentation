/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v2_0;

import static io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbExceptionEventExtractors.setDbClientExceptionEventExtractor;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DbConfig;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientSpanNameExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbSchemaUrls;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.javaagent.instrumentation.couchbase.common.v2_0.CouchbaseRequestInfo;

public class CouchbaseSingletons {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.couchbase-2.0";

  private static final Instrumenter<CouchbaseRequestInfo, Void> instrumenter;
  private static final Instrumenter<CouchbaseRequestInfo, Void> queryInstrumenter;

  static {
    DeclarativeConfigProperties config =
        DeclarativeConfigUtil.getInstrumentationConfig(GlobalOpenTelemetry.get(), "couchbase");
    boolean captureExperimentalTelemetry =
        config.getBoolean("emit_experimental_telemetry/development", false);

    CouchbaseAttributesGetter couchbaseAttributesGetter = new CouchbaseAttributesGetter();
    SpanNameExtractor<CouchbaseRequestInfo> spanNameExtractor =
        DbClientSpanNameExtractor.create(couchbaseAttributesGetter);

    instrumenter =
        buildInstrumenter(
            spanNameExtractor,
            DbClientAttributesExtractor.create(couchbaseAttributesGetter),
            captureExperimentalTelemetry);

    CouchbaseSqlAttributesGetter sqlAttributesGetter = new CouchbaseSqlAttributesGetter();
    queryInstrumenter =
        buildInstrumenter(
            DbClientSpanNameExtractor.create(sqlAttributesGetter),
            SqlClientAttributesExtractor.builder(sqlAttributesGetter)
                .setQuerySanitizationEnabled(
                    DbConfig.isQuerySanitizationEnabled(GlobalOpenTelemetry.get(), "couchbase"))
                .build(),
            captureExperimentalTelemetry);
  }

  private static Instrumenter<CouchbaseRequestInfo, Void> buildInstrumenter(
      SpanNameExtractor<CouchbaseRequestInfo> spanNameExtractor,
      AttributesExtractor<CouchbaseRequestInfo, Void> attributesExtractor,
      boolean captureExperimentalTelemetry) {
    InstrumenterBuilder<CouchbaseRequestInfo, Void> builder =
        Instrumenter.<CouchbaseRequestInfo, Void>builder(
                GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, spanNameExtractor)
            .addAttributesExtractor(attributesExtractor)
            .addContextCustomizer(
                (context, couchbaseRequest, startAttributes) ->
                    CouchbaseRequestInfo.init(context, couchbaseRequest))
            .setSchemaUrl(DbSchemaUrls.SCHEMA_URL)
            .addOperationMetrics(DbClientMetrics.get());
    setDbClientExceptionEventExtractor(builder);

    if (captureExperimentalTelemetry) {
      builder.addAttributesExtractor(new ExperimentalAttributesExtractor());
    }

    return builder.buildInstrumenter(SpanKindExtractor.alwaysClient());
  }

  public static Instrumenter<CouchbaseRequestInfo, Void> instrumenter() {
    return instrumenter;
  }

  public static Instrumenter<CouchbaseRequestInfo, Void> queryInstrumenter() {
    return queryInstrumenter;
  }

  private CouchbaseSingletons() {}
}
