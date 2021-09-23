/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import io.opentelemetry.instrumentation.api.instrumenter.db.DbAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.rpc.RpcAttributesExtractor;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class SpanKeyExtractor {

  /**
   * Automatically determines {@link SpanKey}s that should be applied to the newly constructed
   * {@link Instrumenter} based on the {@link AttributesExtractor}s configured.
   */
  static Set<SpanKey> determineSpanKeys(
      List<? extends AttributesExtractor<?, ?>> attributesExtractors) {
    Set<SpanKey> spanKeys = new HashSet<>();
    for (AttributesExtractor<?, ?> attributeExtractor : attributesExtractors) {
      if (attributeExtractor instanceof HttpAttributesExtractor) {
        spanKeys.add(SpanKey.HTTP_CLIENT);
      } else if (attributeExtractor instanceof RpcAttributesExtractor) {
        spanKeys.add(SpanKey.RPC_CLIENT);
      } else if (attributeExtractor instanceof DbAttributesExtractor) {
        spanKeys.add(SpanKey.DB_CLIENT);
      } else if (attributeExtractor instanceof MessagingAttributesExtractor) {
        spanKeys.add(
            determineMessagingSpanKey((MessagingAttributesExtractor<?, ?>) attributeExtractor));
      }
    }
    return spanKeys;
  }

  private static SpanKey determineMessagingSpanKey(
      MessagingAttributesExtractor<?, ?> messagingAttributesExtractor) {
    switch (messagingAttributesExtractor.operation()) {
      case SEND:
        return SpanKey.PRODUCER;
      case RECEIVE:
        return SpanKey.CONSUMER_RECEIVE;
      case PROCESS:
        return SpanKey.CONSUMER_PROCESS;
    }
    throw new IllegalStateException("Can't possibly happen");
  }

  private SpanKeyExtractor() {}
}
