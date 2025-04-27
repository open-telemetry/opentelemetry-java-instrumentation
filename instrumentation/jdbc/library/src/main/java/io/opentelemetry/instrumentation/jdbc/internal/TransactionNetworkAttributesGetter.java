/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal;

import io.opentelemetry.instrumentation.api.semconv.network.ServerAttributesGetter;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class TransactionNetworkAttributesGetter
    implements ServerAttributesGetter<TransactionRequest> {
  private final JdbcNetworkAttributesGetter jdbcNetworkAttributesGetter =
      new JdbcNetworkAttributesGetter();

  @Nullable
  @Override
  public String getServerAddress(TransactionRequest request) {
    return jdbcNetworkAttributesGetter.getServerAddress(request.getDbInfo());
  }

  @Nullable
  @Override
  public Integer getServerPort(TransactionRequest request) {
    return jdbcNetworkAttributesGetter.getServerPort(request.getDbInfo());
  }
}
