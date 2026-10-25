/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.rest.common.v1_0;

import static io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbExceptionEventExtractors.setDbClientExceptionEventExtractor;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientSpanNameExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbSchemaUrls;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import javax.annotation.Nullable;

public class OpenSearchRestInstrumenterFactory {

  public static Instrumenter<OpenSearchRestRequest, OpenSearchRestResponse> create(
      String instrumentationName, ExceptionStatusCodeExtractor exceptionStatusCodeExtractor) {
    OpenSearchRestAttributesGetter dbClientAttributesGetter =
        new OpenSearchRestAttributesGetter(exceptionStatusCodeExtractor);

    InstrumenterBuilder<OpenSearchRestRequest, OpenSearchRestResponse> builder =
        Instrumenter.<OpenSearchRestRequest, OpenSearchRestResponse>builder(
                GlobalOpenTelemetry.get(),
                instrumentationName,
                DbClientSpanNameExtractor.create(dbClientAttributesGetter))
            .addAttributesExtractor(DbClientAttributesExtractor.create(dbClientAttributesGetter))
            .setSchemaUrl(DbSchemaUrls.SCHEMA_URL)
            .addOperationMetrics(DbClientMetrics.get());
    setDbClientExceptionEventExtractor(builder);
    return builder.buildInstrumenter(SpanKindExtractor.alwaysClient());
  }

  private OpenSearchRestInstrumenterFactory() {}

  @FunctionalInterface
  public interface ExceptionStatusCodeExtractor {
    @Nullable
    Integer getStatusCode(Throwable error);
  }
}
