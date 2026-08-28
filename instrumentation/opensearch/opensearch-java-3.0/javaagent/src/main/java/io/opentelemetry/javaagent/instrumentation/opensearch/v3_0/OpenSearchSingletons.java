/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.v3_0;

import static io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbExceptionEventExtractors.setDbClientExceptionEventExtractor;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

class OpenSearchSingletons {
  private static final Logger logger = Logger.getLogger(OpenSearchSingletons.class.getName());
  private static final AtomicBoolean captureSearchQueryWarningLogged = new AtomicBoolean();

  private static final Instrumenter<OpenSearchRequest, Void> instrumenter = createInstrumenter();

  public static final boolean CAPTURE_SEARCH_QUERY = getCaptureSearchQuery();

  public static Instrumenter<OpenSearchRequest, Void> instrumenter() {
    return instrumenter;
  }

  private static Instrumenter<OpenSearchRequest, Void> createInstrumenter() {
    OpenSearchAttributesGetter dbClientAttributesGetter = new OpenSearchAttributesGetter();

    InstrumenterBuilder<OpenSearchRequest, Void> builder =
        Instrumenter.<OpenSearchRequest, Void>builder(
                GlobalOpenTelemetry.get(),
                "io.opentelemetry.opensearch-java-3.0",
                DbClientSpanNameExtractor.create(dbClientAttributesGetter))
            .addAttributesExtractor(DbClientAttributesExtractor.create(dbClientAttributesGetter))
            .addOperationMetrics(DbClientMetrics.get());
    setDbClientExceptionEventExtractor(builder);
    return builder.buildInstrumenter(SpanKindExtractor.alwaysClient());
  }

  private static boolean getCaptureSearchQuery() {
    // Support the deprecated config key until 3.0.
    if (!SemconvStability.v3Preview()) {
      DeclarativeConfigProperties config =
          DeclarativeConfigUtil.getInstrumentationConfig(GlobalOpenTelemetry.get(), "opensearch");
      Boolean captureSearchQuery = config.getBoolean("capture_search_query");
      if (captureSearchQuery != null) {
        if (captureSearchQueryWarningLogged.compareAndSet(false, true)) {
          logger.warning(
              "The otel.instrumentation.opensearch.capture-search-query setting or equivalent"
                  + " declarative configuration is deprecated and will be removed in 3.0."
                  + " OpenSearch search query bodies will always be captured in 3.0; there is no"
                  + " replacement.");
        }
        return captureSearchQuery;
      }
    }

    return true;
  }

  private OpenSearchSingletons() {}
}
