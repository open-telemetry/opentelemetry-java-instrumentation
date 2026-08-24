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

/**
 * The opt-in flag and span-writing logic shared by the javax ({@link IbmMqJmsQmid}) and jakarta
 * ({@link IbmMqJakartaJmsQmid}) namespace variants of the queue manager identifier enrichment.
 *
 * <p>This class deliberately imports no {@code javax.jms}, {@code jakarta.jms}, or {@code
 * com.ibm.*} type. IBM MQ's javax and jakarta clients are separate, mutually exclusive jars, so
 * muzzle validates each namespace's {@code InstrumentationModule} against its own client
 * independently; if a class referenced from both namespaces' modules carried a reference to either
 * client, that reference would leak into whichever module touches it and fail muzzle validation on
 * the other namespace's classpath. Keeping the namespace-agnostic logic here, with zero MQ/JMS
 * imports, is what makes the two namespaces' modules independently valid.
 */
public final class IbmMqQmidSupport {

  // Registered in the OpenTelemetry semantic conventions messaging registry as opt_in.
  private static final AttributeKey<String> MESSAGING_IBMMQ_QUEUE_MANAGER_ID =
      AttributeKey.stringKey("messaging.ibmmq.queue_manager.id");

  private static final boolean ENABLED =
      DeclarativeConfigUtil.getInstrumentationConfig(GlobalOpenTelemetry.get(), "ibmmq")
          .getBoolean("experimental_span_attributes/development", false);

  /** True when the opt_in attribute has been explicitly enabled. */
  public static boolean enabled() {
    return ENABLED;
  }

  /**
   * Adds the QMID to the messaging span in the current context, if there is one.
   *
   * <p>The span is resolved through {@link SpanKey} rather than {@link Span#current()} so that the
   * attribute can only ever land on a span opened by messaging instrumentation. Were the generic
   * JMS instrumentation disabled or suppressed, {@code Span.current()} would be the application's
   * own unrelated span, and enriching that would be wrong.
   */
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
