/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ibmmq;

import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;
import static java.util.logging.Level.FINE;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.api.internal.SpanKey;
import java.util.logging.Logger;

// Shared by the javax and jakarta variants; references neither client type. Muzzle collects
// references per class, and the two MQ client jars are disjoint, so a leaked reference would fail.
public class IbmMqQmidSupport {

  private static final Logger logger = Logger.getLogger(IbmMqQmidSupport.class.getName());

  // Proposed for the OpenTelemetry semantic conventions messaging registry; not yet merged.
  private static final AttributeKey<String> MESSAGING_IBMMQ_QUEUE_MANAGER_ID =
      AttributeKey.stringKey("messaging.ibmmq.queue_manager.id");

  private static final boolean ENABLED =
      DeclarativeConfigUtil.getInstrumentationConfig(GlobalOpenTelemetry.get(), "ibmmq")
          .getBoolean("experimental_span_attributes/development", false);

  public static boolean enabled() {
    return ENABLED;
  }

  // Resolved via the caller's own SpanKey, never a PRODUCER/CONSUMER_PROCESS/CONSUMER_RECEIVE
  // fallback chain: SpanKey constants are global and shared by every messaging instrumentation, so
  // a fallback can hand back another system's span (e.g. an enclosing Kafka consumer-process span,
  // grabbed only because this call's own producer span happened to be null) and this would then
  // overwrite that unrelated span's messaging.system and QMID. Naming the exact key keeps this
  // scoped to the span shape the caller is actually enriching.
  public static void stampMessagingSpan(SpanKey spanKey, String qmid) {
    try {
      Span span = spanKey.fromContextOrNull(Context.current());
      if (span != null && span.isRecording()) {
        span.setAttribute(MESSAGING_IBMMQ_QUEUE_MANAGER_ID, qmid);
      }
    } catch (Throwable t) {
      logger.log(FINE, "Failed to stamp queue manager id on messaging span", t);
    }
  }

  // "ibmmq" is not a value in opentelemetry-semconv-incubating's MessagingSystemIncubatingValues
  // (only jms, kafka, rabbitmq are) because the messaging.ibmmq.queue_manager.id semantic
  // convention is not yet ratified, so this overwrite of the generic JMS instrumentation's "jms"
  // value must stay behind the same experimental flag as the QMID attribute above, gated by the
  // caller checking enabled() first, the same way stampMessagingSpan(SpanKey, String) above relies
  // on it.
  public static void stampMessagingSystem(SpanKey spanKey) {
    try {
      Span span = spanKey.fromContextOrNull(Context.current());
      if (span != null && span.isRecording()) {
        span.setAttribute(MESSAGING_SYSTEM, "ibmmq");
      }
    } catch (Throwable t) {
      logger.log(FINE, "Failed to stamp messaging system on messaging span", t);
    }
  }

  private IbmMqQmidSupport() {}
}
