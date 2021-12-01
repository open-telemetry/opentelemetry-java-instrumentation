/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rabbitmq;

import com.rabbitmq.client.GetResponse;
import io.opentelemetry.instrumentation.api.instrumenter.TimeExtractor;
import java.time.Instant;
import javax.annotation.Nullable;

class RabbitReceiveTimeExtractor implements TimeExtractor<ReceiveRequest, GetResponse> {

  @Override
  public Instant extractStartTime(ReceiveRequest request) {
    return request.startTime();
  }

  @Override
  public Instant extractEndTime(
      ReceiveRequest request, @Nullable GetResponse response, @Nullable Throwable error) {
    return request.now();
  }
}
