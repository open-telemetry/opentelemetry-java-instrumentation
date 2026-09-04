/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spymemcached.v2_12;

import static io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbExceptionEventExtractors.setDbClientExceptionEventExtractor;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientSpanNameExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTarget;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTargetBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import java.net.InetSocketAddress;
import java.util.List;
import javax.annotation.Nullable;
import net.spy.memcached.MemcachedConnection;

public class SpymemcachedSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.spymemcached-2.12";
  private static final int DEFAULT_PORT = 11211;
  private static final int MAX_ENDPOINT_COUNT = 5;

  private static final Instrumenter<SpymemcachedRequest, Object> instrumenter;
  private static final VirtualField<MemcachedConnection, DbServerTarget> CONFIGURED_TARGETS =
      VirtualField.find(MemcachedConnection.class, DbServerTarget.class);

  static {
    SpymemcachedAttributesGetter dbAttributesGetter = new SpymemcachedAttributesGetter();
    InstrumenterBuilder<SpymemcachedRequest, Object> builder =
        Instrumenter.<SpymemcachedRequest, Object>builder(
                GlobalOpenTelemetry.get(),
                INSTRUMENTATION_NAME,
                DbClientSpanNameExtractor.create(dbAttributesGetter))
            .addAttributesExtractor(DbClientAttributesExtractor.create(dbAttributesGetter))
            .addAttributesExtractor(new SpymemcachedServerAttributesExtractor())
            .addContextCustomizer(
                (context, request, attributes) -> SpymemcachedRequestHolder.init(context, request))
            .addOperationMetrics(DbClientMetrics.get());
    setDbClientExceptionEventExtractor(builder);

    instrumenter = builder.buildInstrumenter(SpanKindExtractor.alwaysClient());
  }

  public static Instrumenter<SpymemcachedRequest, Object> instrumenter() {
    return instrumenter;
  }

  @Nullable
  static DbServerTarget serverTarget(MemcachedConnection connection) {
    return CONFIGURED_TARGETS.get(connection);
  }

  public static void setServerTarget(
      @Nullable MemcachedConnection connection, @Nullable List<InetSocketAddress> nodes) {
    if (connection == null) {
      return;
    }
    CONFIGURED_TARGETS.set(connection, createServerTarget(nodes));
  }

  @Nullable
  static DbServerTarget createServerTarget(@Nullable List<InetSocketAddress> nodes) {
    if (nodes == null) {
      return null;
    }
    DbServerTargetBuilder builder =
        DbServerTarget.builder(DEFAULT_PORT).setSorted(false).setMaxEndpoints(MAX_ENDPOINT_COUNT);
    for (InetSocketAddress node : nodes) {
      builder.addEndpoint(node);
    }
    return builder.build();
  }

  private SpymemcachedSingletons() {}
}
