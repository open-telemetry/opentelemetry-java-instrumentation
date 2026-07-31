/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmqclient.v4_8;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.instrumentation.api.instrumenter.SpanLinksBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanLinksExtractor;
import io.opentelemetry.instrumentation.api.internal.PropagatorBasedSpanLinksExtractor;
import org.apache.rocketmq.common.message.MessageExt;

final class RocketMqReceiveSpanLinksExtractor
    implements SpanLinksExtractor<RocketMqConsumerRequest> {

  private final SpanLinksExtractor<MessageExt> singleMessageLinkExtractor;

  RocketMqReceiveSpanLinksExtractor(TextMapPropagator propagator) {
    this.singleMessageLinkExtractor =
        new PropagatorBasedSpanLinksExtractor<>(propagator, new MessageExtractAdapter());
  }

  @Override
  public void extract(
      SpanLinksBuilder spanLinks, Context parentContext, RocketMqConsumerRequest request) {
    for (MessageExt message : request.getMessages()) {
      singleMessageLinkExtractor.extract(spanLinks, parentContext, message);
    }
  }
}
