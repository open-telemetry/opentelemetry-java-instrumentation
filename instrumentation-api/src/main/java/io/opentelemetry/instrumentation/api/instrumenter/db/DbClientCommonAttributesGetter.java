/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.db;

import javax.annotation.Nullable;

/** An interface for getting attributes common to database clients. */
public interface DbClientCommonAttributesGetter<REQUEST> {

  @Nullable
  String system(REQUEST request);

  @Nullable
  String user(REQUEST request);

  @Nullable
  String name(REQUEST request);

  @Nullable
  String connectionString(REQUEST request);
}
