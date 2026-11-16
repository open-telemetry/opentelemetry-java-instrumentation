/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spymemcached.v2_12;

import static io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbExceptionEventExtractors.setDbClientExceptionEventExtractor;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientSpanNameExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbSchemaUrls;
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
import net.spy.memcached.internal.BulkGetFuture;
import net.spy.memcached.internal.GetFuture;
import net.spy.memcached.internal.OperationFuture;
import net.spy.memcached.ops.OperationStatus;

public class SpymemcachedSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.spymemcached-2.12";
  private static final int DEFAULT_PORT = 11211;

  private static final Instrumenter<SpymemcachedRequest, Object> instrumenter;
  private static final VirtualField<OperationFuture<?>, OperationStatus> OPERATION_FUTURE_STATUS =
      VirtualField.find(OperationFuture.class, OperationStatus.class);
  private static final VirtualField<GetFuture<?>, OperationStatus> GET_FUTURE_STATUS =
      VirtualField.find(GetFuture.class, OperationStatus.class);
  private static final VirtualField<BulkGetFuture<?>, OperationStatus> BULK_FUTURE_STATUS =
      VirtualField.find(BulkGetFuture.class, OperationStatus.class);
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
            .setSpanStatusExtractor(
                (status, request, response, error) -> {
                  if (error != null
                      || dbAttributesGetter.getErrorType(request, response, error) != null) {
                    status.setStatus(StatusCode.ERROR);
                  }
                })
            .addContextCustomizer(
                (context, request, attributes) -> SpymemcachedRequestContext.init(context, request))
            .setSchemaUrl(DbSchemaUrls.SCHEMA_URL)
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

  static void captureResponseStatus(Object future, OperationStatus status) {
    if (future instanceof OperationFuture) {
      OPERATION_FUTURE_STATUS.set((OperationFuture<?>) future, status);
    } else if (future instanceof GetFuture) {
      GET_FUTURE_STATUS.set((GetFuture<?>) future, status);
    } else if (future instanceof BulkGetFuture) {
      BULK_FUTURE_STATUS.set((BulkGetFuture<?>) future, status);
    }
  }

  @Nullable
  static OperationStatus getResponseStatus(@Nullable Object response) {
    if (response instanceof OperationFuture) {
      return OPERATION_FUTURE_STATUS.get((OperationFuture<?>) response);
    }
    if (response instanceof GetFuture) {
      return GET_FUTURE_STATUS.get((GetFuture<?>) response);
    }
    if (response instanceof BulkGetFuture) {
      return BULK_FUTURE_STATUS.get((BulkGetFuture<?>) response);
    }
    return null;
  }

  @Nullable
  static DbServerTarget createServerTarget(@Nullable List<InetSocketAddress> nodes) {
    if (nodes == null) {
      return null;
    }
    DbServerTargetBuilder builder = DbServerTarget.builder(DEFAULT_PORT);
    for (InetSocketAddress node : nodes) {
      builder.addEndpoint(node);
    }
    return builder.build();
  }

  private SpymemcachedSingletons() {}
}
