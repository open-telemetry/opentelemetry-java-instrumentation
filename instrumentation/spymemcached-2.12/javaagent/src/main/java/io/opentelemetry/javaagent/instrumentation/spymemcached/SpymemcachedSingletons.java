/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spymemcached;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.semconv.network.ServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import java.util.concurrent.Future;
import net.spy.memcached.MemcachedNode;
import net.spy.memcached.ops.Operation;

public final class SpymemcachedSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.spymemcached-2.12";

  private static final Instrumenter<SpymemcachedRequest, Object> INSTRUMENTER;

  public static final VirtualField<Future<?>, Operation> FUTURE_OPERATION;

  public static final ThreadLocal<MemcachedNode> handlingNodeThreadLocal = new ThreadLocal<>();

  static {
    SpymemcachedAttributesGetter dbAttributesGetter = new SpymemcachedAttributesGetter();
    SpymemcachedNetworkAttributesGetter netAttributesGetter =
        new SpymemcachedNetworkAttributesGetter();

    INSTRUMENTER =
        Instrumenter.builder(
                GlobalOpenTelemetry.get(),
                INSTRUMENTATION_NAME,
                DbClientSpanNameExtractor.create(dbAttributesGetter))
            .addAttributesExtractor(DbClientAttributesExtractor.create(dbAttributesGetter))
            .addAttributesExtractor(ServerAttributesExtractor.create(netAttributesGetter))
            .addOperationMetrics(DbClientMetrics.get())
            .buildInstrumenter(SpanKindExtractor.alwaysClient());

    FUTURE_OPERATION = VirtualField.find(Future.class, Operation.class);
  }

  public static Instrumenter<SpymemcachedRequest, Object> instrumenter() {
    return INSTRUMENTER;
  }

  private SpymemcachedSingletons() {}
}
