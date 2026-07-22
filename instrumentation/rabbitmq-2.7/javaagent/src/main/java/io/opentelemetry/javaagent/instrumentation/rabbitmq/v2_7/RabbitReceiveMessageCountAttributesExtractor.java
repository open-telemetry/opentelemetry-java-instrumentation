/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rabbitmq.v2_7;

import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_BATCH_MESSAGE_COUNT;

import com.rabbitmq.client.GetResponse;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import javax.annotation.Nullable;

final class RabbitReceiveMessageCountAttributesExtractor
    implements AttributesExtractor<ReceiveRequest, GetResponse> {

  @Override
  public void onStart(
      AttributesBuilder attributes, Context parentContext, ReceiveRequest request) {}

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      ReceiveRequest request,
      @Nullable GetResponse response,
      @Nullable Throwable error) {
    attributes.put(MESSAGING_BATCH_MESSAGE_COUNT, request.getResponse() == null ? 0 : 1);
  }
}
