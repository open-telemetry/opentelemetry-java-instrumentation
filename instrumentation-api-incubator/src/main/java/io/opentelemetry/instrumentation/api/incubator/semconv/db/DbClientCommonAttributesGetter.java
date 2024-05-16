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

  @Nullable
  String getConnectionString(REQUEST request);
}
