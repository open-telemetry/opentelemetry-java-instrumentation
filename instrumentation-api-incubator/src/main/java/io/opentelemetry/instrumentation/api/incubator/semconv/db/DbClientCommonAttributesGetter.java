/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db;

import static java.util.Objects.requireNonNull;

import javax.annotation.Nullable;

/** An interface for getting attributes common to database clients. */
public interface DbClientCommonAttributesGetter<REQUEST, RESPONSE> {

  @Deprecated
  @Nullable
  default String getSystem(REQUEST request) {
    return null;
  }

  // TODO: make this required to implement
  default String getDbSystem(REQUEST request) {
    // this method will become abstract in
    // https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/14460
    return requireNonNull(getSystem(request));
  }

  @Deprecated
  @Nullable
  default String getUser(REQUEST request) {
    return null;
  }

  /**
   * @deprecated Use {@link #getDbNamespace(Object)} instead.
   */
  @Deprecated
  @Nullable
  default String getName(REQUEST request) {
    return null;
  }

  // TODO: make this required to implement
  @Nullable
  default String getDbNamespace(REQUEST request) {
    return getName(request);
  }

  @Deprecated
  @Nullable
  default String getConnectionString(REQUEST request) {
    return null;
  }

  @Nullable
  default String getResponseStatus(@Nullable RESPONSE response, @Nullable Throwable error) {
    return null;
  }
}
