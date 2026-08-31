/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ibmmq;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.api.internal.SpanKey;
import javax.annotation.Nullable;

// Shared by the javax and jakarta variants; references neither client type. Muzzle collects
// references per class, and the two MQ client jars are disjoint, so a leaked reference would fail.
public class IbmMqQmidSupport {

  // Proposed for the OpenTelemetry semantic conventions messaging registry; not yet merged.
  private static final AttributeKey<String> MESSAGING_IBMMQ_QUEUE_MANAGER_ID =
      AttributeKey.stringKey("messaging.ibmmq.queue_manager.id");

  private static final boolean ENABLED =
      DeclarativeConfigUtil.getInstrumentationConfig(GlobalOpenTelemetry.get(), "ibmmq")
          .getBoolean("experimental_span_attributes/development", false);

  public static boolean enabled() {
    return ENABLED;
  }

  // Resolved via SpanKey rather than Span.current() so the attribute can only land on a span opened
  // by messaging instrumentation, never on the application's own unrelated span.
  public static void stampMessagingSpan(String qmid) {
    try {
      Span span = messagingSpan(Context.current());
      if (span != null && span.isRecording()) {
        span.setAttribute(MESSAGING_IBMMQ_QUEUE_MANAGER_ID, qmid);
      }
    } catch (Throwable t) {
      // best-effort
    }
  }

  @Nullable
  private static Span messagingSpan(Context context) {
    Span span = SpanKey.PRODUCER.fromContextOrNull(context);
    if (span == null) {
      span = SpanKey.CONSUMER_PROCESS.fromContextOrNull(context);
    }
    if (span == null) {
      span = SpanKey.CONSUMER_RECEIVE.fromContextOrNull(context);
    }
    return span;
  }

  private IbmMqQmidSupport() {}
}
