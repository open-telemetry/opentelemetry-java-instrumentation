/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.clickhouse;

import io.opentelemetry.instrumentation.api.semconv.network.ServerAttributesGetter;

final class ClickHouseNetworkAttributesGetter
    implements ServerAttributesGetter<ClickHouseDbRequest> {

  @Override
  public String getServerAddress(ClickHouseDbRequest request) {
    return request.getHost();
  }

  @Override
  public Integer getServerPort(ClickHouseDbRequest request) {
    return request.getPort();
  }
}
