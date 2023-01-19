/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.db;

import javax.annotation.Nullable;

/** An interface for getting attributes common to database clients. */
public interface DbClientCommonAttributesGetter<REQUEST> {

  @Nullable
  default String getSystem(REQUEST request) {
    return system(request);
  }

  /**
   * This method is deprecated and will be removed in the subsequent release.
   *
   * @deprecated Use {@link #getSystem(Object)} instead.
   */
  @Deprecated
  @Nullable
  default String system(REQUEST request) {
    throw new UnsupportedOperationException(
        "This method is deprecated and will be removed in the subsequent release.");
  }

  @Nullable
  default String getUser(REQUEST request) {
    return user(request);
  }

  /**
   * This method is deprecated and will be removed in the subsequent release.
   *
   * @deprecated Use {@link #getUser(Object)} instead.
   */
  @Deprecated
  @Nullable
  default String user(REQUEST request) {
    throw new UnsupportedOperationException(
        "This method is deprecated and will be removed in the subsequent release.");
  }

  @Nullable
  default String getName(REQUEST request) {
    return name(request);
  }

  /**
   * This method is deprecated and will be removed in the subsequent release.
   *
   * @deprecated Use {@link #getName(Object)} instead.
   */
  @Deprecated
  @Nullable
  default String name(REQUEST request) {
    throw new UnsupportedOperationException(
        "This method is deprecated and will be removed in the subsequent release.");
  }

  @Nullable
  default String getConnectionString(REQUEST request) {
    return connectionString(request);
  }

  /**
   * This method is deprecated and will be removed in the subsequent release.
   *
   * @deprecated Use {@link #getConnectionString(Object)} instead.
   */
  @Deprecated
  @Nullable
  default String connectionString(REQUEST request) {
    throw new UnsupportedOperationException(
        "This method is deprecated and will be removed in the subsequent release.");
  }
}
