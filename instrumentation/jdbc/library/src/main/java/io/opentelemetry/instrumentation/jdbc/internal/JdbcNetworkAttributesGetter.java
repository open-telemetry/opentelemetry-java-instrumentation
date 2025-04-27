/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal;

import io.opentelemetry.instrumentation.api.semconv.network.ServerAttributesGetter;
import io.opentelemetry.instrumentation.jdbc.internal.dbinfo.DbInfo;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class JdbcNetworkAttributesGetter implements ServerAttributesGetter<DbInfo> {

  @Nullable
  @Override
  public String getServerAddress(DbInfo request) {
    return request.getHost();
  }

  @Nullable
  @Override
  public Integer getServerPort(DbInfo request) {
    return request.getPort();
  }
}
