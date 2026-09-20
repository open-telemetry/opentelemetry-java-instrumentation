/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.messaging;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static java.util.Objects.requireNonNull;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongCounterBuilder;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.internal.OperationMetricsUtil;
import javax.annotation.Nullable;

/**
 * Records explicit delivery counts in {@code messaging.client.consumed.messages}.
 *
 * <p>Callers determine which deliveries to count and prevent duplicate recording. This recorder
 * keeps no per-delivery state and does not record operation durations.
 */
public final class MessagingConsumedMessagesRecorder {
  private static final MessagingConsumedMessagesRecorder NOOP =
      new MessagingConsumedMessagesRecorder(null);

  @Nullable private final LongCounter counter;

  /**
   * Creates a recorder using the supplied meter's instrumentation scope.
   *
   * <p>Recording is disabled when stable messaging semantic conventions are disabled or the meter
   * does not support the SDK metrics-advice compatibility policy. Accepted no-op meters remain
   * supported.
   */
  public static MessagingConsumedMessagesRecorder create(Meter meter) {
    requireNonNull(meter, "meter");
    if (!emitStableMessagingSemconv()
        || !OperationMetricsUtil.supportsMetricsAdvice("messaging consumed messages", meter)) {
      return NOOP;
    }
    LongCounterBuilder builder =
        meter
            .counterBuilder("messaging.client.consumed.messages")
            .setDescription("Number of messages that were delivered to the application.")
            .setUnit("{message}");
    MessagingMetricsAdvice.applyConsumedMessagesAdvice(builder);
    return new MessagingConsumedMessagesRecorder(builder.build());
  }

  // visible for testing
  MessagingConsumedMessagesRecorder(@Nullable LongCounter counter) {
    this.counter = counter;
  }

  /**
   * Records {@code count} delivered messages, using the supplied context for exemplar correlation
   * without making it current.
   *
   * <p>The count is authoritative, independent of batch-size attributes, operation type, errors,
   * sampling, and ambient messaging telemetry state. End attributes override start attributes. The
   * existing consumed-message attribute advice applies, and destination names are omitted when a
   * template is present or the destination is temporary or anonymous. Neither input is modified.
   *
   * @return {@code true} when a positive count's synchronous counter call completes, including with
   *     an accepted no-op meter; {@code false} for zero or disabled recording. This is not an
   *     acknowledgment of export or backend receipt. Recording exceptions propagate to the caller.
   * @throws IllegalArgumentException if {@code count} is negative, even when recording is disabled
   */
  @CanIgnoreReturnValue
  public boolean record(
      long count, Attributes startAttributes, Attributes endAttributes, Context context) {
    requireNonNull(startAttributes, "startAttributes");
    requireNonNull(endAttributes, "endAttributes");
    requireNonNull(context, "context");
    if (count < 0) {
      throw new IllegalArgumentException("count must not be negative");
    }
    if (count == 0 || counter == null) {
      return false;
    }
    Attributes attributes =
        endAttributes.isEmpty()
            ? startAttributes
            : startAttributes.toBuilder().putAll(endAttributes).build();
    counter.add(count, MessagingMetricsAdvice.filterAttributes(attributes), context);
    return true;
  }
}
