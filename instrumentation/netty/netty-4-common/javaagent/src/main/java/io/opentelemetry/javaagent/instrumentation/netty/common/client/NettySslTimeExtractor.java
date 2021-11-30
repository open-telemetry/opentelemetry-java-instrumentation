/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.common.client;

import io.opentelemetry.instrumentation.api.instrumenter.TimeExtractor;
import java.time.Instant;
import javax.annotation.Nullable;

class NettySslTimeExtractor implements TimeExtractor<NettySslRequest, Void> {

  @Override
  public Instant extractStartTime(NettySslRequest request) {
    return request.timer().startTime();
  }

  @Override
  public Instant extractEndTime(
      NettySslRequest request, @Nullable Void unused, @Nullable Throwable error) {
    return request.timer().now();
  }
}
