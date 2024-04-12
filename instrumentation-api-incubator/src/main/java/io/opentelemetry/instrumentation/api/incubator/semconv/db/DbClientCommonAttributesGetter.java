/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db;

import javax.annotation.Nullable;

/** An interface for getting attributes common to database clients. */
public interface DbClientCommonAttributesGetter<REQUEST> {

  @Nullable
  String getSystem(REQUEST request);

  @Nullable
  String getUser(REQUEST request);

  @Nullable
  String getName(REQUEST request);

  // to be removed in 2.4.0, use `server.address` and `server.port` instead
  @Nullable
  @Deprecated
  default String getConnectionString(REQUEST request) {
    return null;
  }
}
