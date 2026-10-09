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
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.javaagent.instrumentation.couchbase.common.v2_0.CouchbaseRequestInfo;
import java.util.logging.Logger;

public class CouchbaseSingletons {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.couchbase-2.0";
  private static final Logger logger = Logger.getLogger(CouchbaseSingletons.class.getName());

  private static final Instrumenter<CouchbaseRequestInfo, Void> instrumenter;
  private static final Instrumenter<CouchbaseRequestInfo, Void> queryInstrumenter;

  static {
    DeclarativeConfigProperties config =
        DeclarativeConfigUtil.getInstrumentationConfig(GlobalOpenTelemetry.get(), "couchbase");
    boolean captureExperimentalTelemetry = captureExperimentalTelemetry(config);

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

  private static boolean captureExperimentalTelemetry(DeclarativeConfigProperties config) {
    Boolean configured = config.getBoolean("emit_experimental_telemetry/development");
    if (configured != null) {
      return configured;
    }

    // Deprecated; remains active until the next minor release.
    Boolean deprecated = config.getBoolean("experimental_span_attributes/development");
    if (deprecated == null) {
      return false;
    }

    logger.warning(
        "The otel.instrumentation.couchbase.experimental-span-attributes setting and the"
            + " equivalent declarative configuration property are deprecated and will be removed"
            + " in the next minor release. Use"
            + " otel.instrumentation.couchbase.emit-experimental-telemetry or the equivalent"
            + " declarative configuration instead.");
    return deprecated;
  }

  private CouchbaseSingletons() {}
}
