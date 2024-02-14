/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pubsub.subscriber;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.instrumentation.pubsub.PubsubAttributes;

public class AckReplyHelper {
  private AckReplyHelper() {}

  public static void ack(Context context) {
    Span.fromContext(context)
        .setAttribute(PubsubAttributes.ACK_RESULT, PubsubAttributes.AckResultValues.ACK);
  }

  public static void nack(Context context) {
    Span.fromContext(context)
        .setAttribute(PubsubAttributes.ACK_RESULT, PubsubAttributes.AckResultValues.NACK);
  }
}
