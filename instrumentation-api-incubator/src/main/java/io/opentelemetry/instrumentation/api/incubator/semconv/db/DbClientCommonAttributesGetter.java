/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db;

import javax.annotation.Nullable;

/** An interface for getting attributes common to database clients. */
public interface DbClientCommonAttributesGetter<REQUEST, RESPONSE> {

  String getDbSystem(REQUEST request);

  @Deprecated
  @Nullable
  default String getUser(REQUEST request) {
    return null;
  }

  @Nullable
  String getDbNamespace(REQUEST request);

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
