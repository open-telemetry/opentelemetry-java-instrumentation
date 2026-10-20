/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v3_0;

import static io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbExceptionEventExtractors.setDbClientExceptionEventExtractor;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisSpanNameExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.service.peer.ServicePeerAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusExtractor;
import io.opentelemetry.javaagent.instrumentation.jedis.common.v1_4.JedisBatchResponse;

public class JedisSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.jedis-3.0";

  private static final Instrumenter<JedisRequest, JedisBatchResponse> instrumenter;

  static {
    JedisDbAttributesGetter dbAttributesGetter = new JedisDbAttributesGetter();

    InstrumenterBuilder<JedisRequest, JedisBatchResponse> builder =
        Instrumenter.<JedisRequest, JedisBatchResponse>builder(
                GlobalOpenTelemetry.get(),
                INSTRUMENTATION_NAME,
                RedisSpanNameExtractor.create(dbAttributesGetter))
            .addAttributesExtractor(DbClientAttributesExtractor.create(dbAttributesGetter))
            .addAttributesExtractor(
                ServicePeerAttributesExtractor.create(
                    dbAttributesGetter, GlobalOpenTelemetry.get()))
            .addOperationMetrics(DbClientMetrics.get())
            .setSpanStatusExtractor(
                (spanStatusBuilder, request, response, error) -> {
                  if (error != null) {
                    SpanStatusExtractor.getDefault()
                        .extract(spanStatusBuilder, request, response, error);
                  } else if (response != null && response.isFailed()) {
                    spanStatusBuilder.setStatus(StatusCode.ERROR);
                  } else {
                    SpanStatusExtractor.getDefault()
                        .extract(spanStatusBuilder, request, response, error);
                  }
                });
    setDbClientExceptionEventExtractor(builder);

    instrumenter = builder.buildInstrumenter(SpanKindExtractor.alwaysClient());
  }

  public static Instrumenter<JedisRequest, JedisBatchResponse> instrumenter() {
    return instrumenter;
  }

  private JedisSingletons() {}
}
