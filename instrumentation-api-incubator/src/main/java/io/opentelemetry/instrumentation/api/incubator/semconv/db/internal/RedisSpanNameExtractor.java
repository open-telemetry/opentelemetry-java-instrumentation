/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db.internal;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import javax.annotation.Nullable;

/**
 * Creates Redis span names without including the database namespace.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class RedisSpanNameExtractor {

  public static <REQUEST> SpanNameExtractor<REQUEST> create(
      DbClientAttributesGetter<REQUEST, ?> getter) {
    return DbClientSpanNameExtractor.create(new RedisSpanNameDbAttributesGetter<>(getter));
  }

  private RedisSpanNameExtractor() {}

  private static final class RedisSpanNameDbAttributesGetter<REQUEST>
      implements DbClientAttributesGetter<REQUEST, Object> {

    private final DbClientAttributesGetter<REQUEST, ?> delegate;

    private RedisSpanNameDbAttributesGetter(DbClientAttributesGetter<REQUEST, ?> delegate) {
      this.delegate = delegate;
    }

    @Override
    @Nullable
    public String getDbQueryText(REQUEST request) {
      return null;
    }

    @Override
    @Nullable
    public String getDbQuerySummary(REQUEST request) {
      return delegate.getDbQuerySummary(request);
    }

    @Override
    @Nullable
    public String getDbOperationName(REQUEST request) {
      return delegate.getDbOperationName(request);
    }

    @SuppressWarnings("deprecation") // getDbOperation is used for old semconv span names
    @Override
    @Nullable
    public String getDbOperation(REQUEST request) {
      return delegate.getDbOperation(request);
    }

    @Override
    public String getDbSystemName(REQUEST request) {
      return delegate.getDbSystemName(request);
    }

    @Override
    @Nullable
    public String getDbNamespace(REQUEST request) {
      return null;
    }

    @Override
    @Nullable
    public String getDbCollectionName(REQUEST request) {
      return delegate.getDbCollectionName(request);
    }

    @Override
    @Nullable
    public String getServerAddress(REQUEST request) {
      return delegate.getServerAddress(request);
    }

    @Override
    @Nullable
    public Integer getServerPort(REQUEST request) {
      return delegate.getServerPort(request);
    }
  }
}
