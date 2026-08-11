/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ibmmq;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import javax.annotation.Nullable;

class IbmMqAttributesExtractor implements AttributesExtractor<IbmMqRequest, IbmMqResponse> {

  private static final AttributeKey<String> IBM_MQ_QUEUE_MANAGER_ID =
      AttributeKey.stringKey("messaging.ibmmq.queue_manager.id");

  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext, IbmMqRequest request) {
    String queueManagerId = request.getQueueManagerId();
    if (queueManagerId != null && !queueManagerId.isEmpty()) {
      attributes.put(IBM_MQ_QUEUE_MANAGER_ID, queueManagerId);
    }
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      IbmMqRequest request,
      @Nullable IbmMqResponse response,
      @Nullable Throwable error) {}
}
