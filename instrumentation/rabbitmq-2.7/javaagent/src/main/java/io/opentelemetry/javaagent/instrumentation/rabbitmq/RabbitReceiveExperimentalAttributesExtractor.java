/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rabbitmq;

import static io.opentelemetry.javaagent.instrumentation.rabbitmq.RabbitInstrumenterHelper.RABBITMQ_COMMAND;

import com.rabbitmq.client.GetResponse;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import javax.annotation.Nullable;

class RabbitReceiveExperimentalAttributesExtractor
    implements AttributesExtractor<ReceiveRequest, GetResponse> {
  private static final AttributeKey<String> RABBITMQ_QUEUE =
      AttributeKey.stringKey("rabbitmq.queue");

  @Override
  public void onStart(AttributesBuilder attributes, ReceiveRequest receiveRequest) {
    set(attributes, RABBITMQ_COMMAND, "basic.get");
    set(attributes, RABBITMQ_QUEUE, receiveRequest.getQueue());
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      ReceiveRequest receiveRequest,
      @Nullable GetResponse response,
      @Nullable Throwable error) {}
}
