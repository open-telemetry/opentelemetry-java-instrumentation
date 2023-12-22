/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.r2dbc.v1_0.internal;

import io.opentelemetry.instrumentation.api.semconv.network.ServerAttributesGetter;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public enum R2dbcNetAttributesGetter implements ServerAttributesGetter<DbExecution> {
  INSTANCE;

  @Nullable
  @Override
  public String getServerAddress(DbExecution request) {
    return request.getHost();
  }

  @Nullable
  @Override
  public Integer getServerPort(DbExecution request) {
    return request.getPort();
  }
}
