/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.telemetry;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter;
import javax.annotation.Nullable;

public final class PulsarNetClientAttributesGetter
    implements NetClientAttributesGetter<BasePulsarRequest, Void> {
  @Nullable
  @Override
  public String getTransport(BasePulsarRequest request, @Nullable Void unused) {
    return null;
  }

  @Nullable
  @Override
  public String getPeerName(BasePulsarRequest request) {
    return request.getUrlData() != null ? request.getUrlData().getHost() : null;
  }

  @Nullable
  @Override
  public Integer getPeerPort(BasePulsarRequest request) {
    return request.getUrlData() != null ? request.getUrlData().getPort() : null;
  }
}
