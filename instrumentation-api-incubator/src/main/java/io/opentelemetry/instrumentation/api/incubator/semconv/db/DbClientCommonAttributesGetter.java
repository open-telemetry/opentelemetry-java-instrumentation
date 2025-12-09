/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db;

import javax.annotation.Nullable;

/**
 * An interface for getting attributes common to database clients.
 *
 * @deprecated Use {@link DbClientAttributesGetter} instead.
 */
@Deprecated
public interface DbClientCommonAttributesGetter<REQUEST, RESPONSE> {

  /**
   * @deprecated Use {@link DbClientAttributesGetter#getDbSystemName} instead.
   */
  @Deprecated
  @Nullable
  String getDbSystem(REQUEST request);

  /**
   * @deprecated There is no replacement at this time.
   */
  @Deprecated
  @Nullable
  default String getUser(REQUEST request) {
    return null;
  }

  @Nullable
  default String getDbNamespace(REQUEST request) {
    return null;
  }

  /**
   * @deprecated There is no replacement at this time.
   */
  @Deprecated
  @Nullable
  default String getConnectionString(REQUEST request) {
    return null;
  }

  /**
   * @deprecated Use {@link DbClientAttributesGetter#getResponseStatusCode} instead.
   */
  @Deprecated
  @Nullable
  default String getResponseStatus(@Nullable RESPONSE response, @Nullable Throwable error) {
    return null;
  }
}
