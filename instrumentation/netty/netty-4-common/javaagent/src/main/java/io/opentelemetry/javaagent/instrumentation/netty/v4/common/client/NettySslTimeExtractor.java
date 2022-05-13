/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4.common.client;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.TimeExtractor;
import java.time.Instant;

class NettySslTimeExtractor implements TimeExtractor<NettySslRequest, Void> {

  @Override
  public Instant extractStartTime(Context parentContext, NettySslRequest request) {
    return request.timer().startTime();
  }

  @Override
  public Instant extractEndTime(Context context, NettySslRequest request) {
    return request.timer().now();
  }
}
