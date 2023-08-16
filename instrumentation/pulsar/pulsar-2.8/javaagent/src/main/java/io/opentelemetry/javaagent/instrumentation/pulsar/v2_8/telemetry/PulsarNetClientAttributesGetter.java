/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.telemetry;

import io.opentelemetry.instrumentation.api.instrumenter.network.ServerAttributesGetter;
import javax.annotation.Nullable;

public final class PulsarNetClientAttributesGetter
    implements ServerAttributesGetter<BasePulsarRequest, Void> {

  @Nullable
  @Override
  public String getServerAddress(BasePulsarRequest request) {
    return request.getUrlData() != null ? request.getUrlData().getHost() : null;
  }

  @Nullable
  @Override
  public Integer getServerPort(BasePulsarRequest request) {
    return request.getUrlData() != null ? request.getUrlData().getPort() : null;
  }
}
