/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.r2dbc.v1_0.internal;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public enum R2dbcNetAttributesGetter implements NetClientAttributesGetter<DbExecution, Void> {
  INSTANCE;

  @Nullable
  @Override
  public String getPeerName(DbExecution request) {
    return request.getHost();
  }

  @Nullable
  @Override
  public Integer getPeerPort(DbExecution request) {
    return request.getPort();
  }
}
