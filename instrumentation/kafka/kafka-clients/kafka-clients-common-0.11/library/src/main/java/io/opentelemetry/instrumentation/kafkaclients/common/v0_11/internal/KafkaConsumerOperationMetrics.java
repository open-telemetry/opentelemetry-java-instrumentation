/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal;

import static java.util.concurrent.TimeUnit.SECONDS;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.metrics.MeterBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingConsumerMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingOperationType;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.ErrorCauseExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.OperationListener;
import io.opentelemetry.instrumentation.api.internal.EmbeddedInstrumentationProperties;
import java.time.Instant;
import javax.annotation.Nullable;

/**
 * Records Kafka consumer operation metrics independently from receive spans.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class KafkaConsumerOperationMetrics {

  private static final String POLL_OPERATION_NAME = "poll";

  private final boolean enabled;
  private final ErrorCauseExtractor errorCauseExtractor;
  private final AttributesExtractor<KafkaReceiveRequest, Void> messagingAttributesExtractor;
  private final AttributesExtractor<KafkaReceiveRequest, Void> kafkaAttributesExtractor =
      new KafkaReceiveAttributesExtractor();
  private final OperationListener listener;

  KafkaConsumerOperationMetrics(
      OpenTelemetry openTelemetry,
      String instrumentationName,
      ErrorCauseExtractor errorCauseExtractor,
      boolean enabled) {
    this.enabled = enabled;
    this.errorCauseExtractor = errorCauseExtractor;
    messagingAttributesExtractor =
        MessagingAttributesExtractor.create(
            new KafkaReceiveAttributesGetter(),
            MessagingOperationType.RECEIVE,
            POLL_OPERATION_NAME);

    MeterBuilder meterBuilder = openTelemetry.meterBuilder(instrumentationName);
    String instrumentationVersion =
        EmbeddedInstrumentationProperties.findVersion(instrumentationName);
    if (instrumentationVersion != null) {
      meterBuilder.setInstrumentationVersion(instrumentationVersion);
    }
    listener = MessagingConsumerMetrics.getClientOperationDuration().create(meterBuilder.build());
  }

  public void recordDuration(
      Context context,
      KafkaReceiveRequest request,
      @Nullable Throwable error,
      Instant startTime,
      Instant endTime) {
    if (!enabled) {
      return;
    }

    AttributesBuilder startAttributes = Attributes.builder();
    messagingAttributesExtractor.onStart(startAttributes, context, request);
    kafkaAttributesExtractor.onStart(startAttributes, context, request);
    Context metricsContext = listener.onStart(context, startAttributes.build(), toNanos(startTime));

    if (error != null) {
      error = errorCauseExtractor.extract(error);
    }
    AttributesBuilder endAttributes = Attributes.builder();
    messagingAttributesExtractor.onEnd(endAttributes, context, request, null, error);
    kafkaAttributesExtractor.onEnd(endAttributes, context, request, null, error);
    listener.onEnd(metricsContext, endAttributes.build(), toNanos(endTime));
  }

  private static long toNanos(Instant time) {
    return SECONDS.toNanos(time.getEpochSecond()) + time.getNano();
  }
}
