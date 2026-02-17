/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spymemcached;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.semconv.network.ServerAttributesExtractor;
import javax.annotation.Nullable;

public final class SpymemcachedSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.spymemcached-2.12";

  private static final Instrumenter<SpymemcachedRequest, Object> INSTRUMENTER;

  static {
    SpymemcachedAttributesGetter dbAttributesGetter = new SpymemcachedAttributesGetter();
    SpymemcachedNetworkAttributesGetter netAttributesGetter =
        new SpymemcachedNetworkAttributesGetter();
    ServerAttributesExtractor<SpymemcachedRequest, Void> serverAttributesExtractor =
        ServerAttributesExtractor.create(netAttributesGetter);

    INSTRUMENTER =
        Instrumenter.builder(
                GlobalOpenTelemetry.get(),
                INSTRUMENTATION_NAME,
                DbClientSpanNameExtractor.create(dbAttributesGetter))
            .addAttributesExtractor(DbClientAttributesExtractor.create(dbAttributesGetter))
            .addAttributesExtractor(
                new AttributesExtractor<SpymemcachedRequest, Object>() {
                  @Override
                  public void onStart(
                      AttributesBuilder attributes, Context context, SpymemcachedRequest request) {}

                  @Override
                  public void onEnd(
                      AttributesBuilder attributes,
                      Context context,
                      SpymemcachedRequest request,
                      @Nullable Object object,
                      @Nullable Throwable error) {
                    // For spymemcached, we can only extract server attributes at the end of the
                    // request because they are not available at the start.
                    serverAttributesExtractor.onStart(attributes, context, request);
                  }
                })
            .addContextCustomizer(
                (context, request, attributes) -> SpymemcachedRequestHolder.init(context, request))
            .addOperationMetrics(DbClientMetrics.get())
            .buildInstrumenter(SpanKindExtractor.alwaysClient());
  }

  public static Instrumenter<SpymemcachedRequest, Object> instrumenter() {
    return INSTRUMENTER;
  }

  private SpymemcachedSingletons() {}
}
