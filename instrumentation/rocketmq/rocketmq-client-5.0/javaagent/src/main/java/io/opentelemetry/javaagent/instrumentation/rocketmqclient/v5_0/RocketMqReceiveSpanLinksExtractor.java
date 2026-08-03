/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rocketmqclient.v5_0;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.instrumentation.api.instrumenter.SpanLinksBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanLinksExtractor;
import io.opentelemetry.instrumentation.api.internal.PropagatorBasedSpanLinksExtractor;
import org.apache.rocketmq.client.apis.message.MessageView;

class RocketMqReceiveSpanLinksExtractor implements SpanLinksExtractor<RocketMqReceiveRequest> {

  private final SpanLinksExtractor<MessageView> singleMessageLinkExtractor;

  RocketMqReceiveSpanLinksExtractor(TextMapPropagator propagator) {
    this.singleMessageLinkExtractor =
        new PropagatorBasedSpanLinksExtractor<>(propagator, new MessageMapGetter());
  }

  @Override
  public void extract(
      SpanLinksBuilder spanLinks, Context parentContext, RocketMqReceiveRequest request) {
    for (MessageView message : request.getMessages()) {
      singleMessageLinkExtractor.extract(spanLinks, parentContext, message);
    }
  }
}
