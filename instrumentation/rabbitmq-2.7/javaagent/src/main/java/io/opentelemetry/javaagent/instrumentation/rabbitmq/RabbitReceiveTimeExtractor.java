/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rabbitmq;

import com.rabbitmq.client.GetResponse;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.TimeExtractor;
import java.time.Instant;

class RabbitReceiveTimeExtractor implements TimeExtractor<ReceiveRequest, GetResponse> {

  @Override
  public Instant extractStartTime(Context parentContext, ReceiveRequest request) {
    return request.startTime();
  }

  @Override
  public Instant extractEndTime(Context context, ReceiveRequest request) {
    return request.now();
  }
}
