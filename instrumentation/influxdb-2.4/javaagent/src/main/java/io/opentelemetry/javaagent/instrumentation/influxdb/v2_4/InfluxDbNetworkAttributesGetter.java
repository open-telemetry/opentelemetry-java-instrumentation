/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.influxdb.v2_4;

import io.opentelemetry.instrumentation.api.semconv.network.ServerAttributesGetter;

final class InfluxDbNetworkAttributesGetter implements ServerAttributesGetter<InfluxDbRequest> {

  @Override
  public String getServerAddress(InfluxDbRequest request) {
    return request.getHost();
  }

  @Override
  public Integer getServerPort(InfluxDbRequest request) {
    return request.getPort();
  }
}
