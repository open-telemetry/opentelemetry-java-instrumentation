/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db;

import javax.annotation.Nullable;

/**
 * An interface for getting database client attributes.
 *
 * <p>Instrumentation authors will create implementations of this interface for their specific
 * library/framework. It will be used by the {@link DbClientAttributesExtractor} to obtain the
 * various database client attributes in a type-generic way.
 *
 * <p>If an attribute is not available in this library, it is appropriate to return {@code null}
 * from the attribute methods, but implement as many as possible for best compliance with the
 * OpenTelemetry specification.
 */
@SuppressWarnings("deprecation") // using deprecated semconv
public interface DbClientAttributesGetter<REQUEST> extends DbClientCommonAttributesGetter<REQUEST> {

  /**
   * @deprecated Use {@link #getDbQueryText(REQUEST)} instead.
   */
  @Deprecated
  @Nullable
  default String getStatement(REQUEST request) {
    return getDbQueryText(request);
  }

  // TODO (trask) add default implementation that returns null
  @Nullable
  String getDbQueryText(REQUEST request);

  /**
   * @deprecated Use {@link #getDbOperationName(REQUEST)} instead.
   */
  @Deprecated
  @Nullable
  default String getOperation(REQUEST request) {
    return getDbOperationName(request);
  }

  // TODO (trask) add default implementation that returns null
  //  after https://github.com/open-telemetry/semantic-conventions/pull/1566
  @Nullable
  String getDbOperationName(REQUEST request);

  @Nullable
  String getDbSystem(REQUEST request);

  @Nullable
  String getDbNamespace(REQUEST request);

  /**
   * @deprecated Use {@link #getDbSystem(Object)} instead.
   */
  @Deprecated
  @Override
  @Nullable
  default String getSystem(REQUEST request) {
    return getDbSystem(request);
  }

  /**
   * @deprecated Use {@link #getDbNamespace(Object)} instead.
   */
  @Deprecated
  @Override
  @Nullable
  default String getName(REQUEST request) {
    return getDbNamespace(request);
  }
}
