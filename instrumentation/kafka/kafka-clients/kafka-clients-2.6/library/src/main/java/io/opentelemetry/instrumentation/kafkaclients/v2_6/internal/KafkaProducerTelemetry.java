/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.v2_6.internal;

import static java.util.logging.Level.WARNING;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaHeadersSetter;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaProducerRequest;
import java.util.logging.Logger;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.header.Headers;

/**
 * Helper for producer-side instrumentation.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public class KafkaProducerTelemetry {
  private static final Logger logger = Logger.getLogger(KafkaProducerTelemetry.class.getName());

  private static final TextMapSetter<Headers> SETTER = KafkaHeadersSetter.INSTANCE;

  private final TextMapPropagator propagator;
  private final Instrumenter<KafkaProducerRequest, RecordMetadata> producerInstrumenter;
  private final boolean producerPropagationEnabled;

  public KafkaProducerTelemetry(
      TextMapPropagator propagator,
      Instrumenter<KafkaProducerRequest, RecordMetadata> producerInstrumenter,
      boolean producerPropagationEnabled) {
    this.propagator = propagator;
    this.producerInstrumenter = producerInstrumenter;
    this.producerPropagationEnabled = producerPropagationEnabled;
  }

  /**
   * Build and inject span into record.
   *
   * @param record the producer record to inject span info.
   */
  public <K, V> void buildAndInjectSpan(ProducerRecord<K, V> record, String clientId) {
    Context parentContext = Context.current();

    KafkaProducerRequest request = KafkaProducerRequest.create(record, clientId);
    if (!producerInstrumenter.shouldStart(parentContext, request)) {
      return;
    }

    Context context = producerInstrumenter.start(parentContext, request);
    if (producerPropagationEnabled) {
      try {
        propagator.inject(context, record.headers(), SETTER);
      } catch (Throwable t) {
        // it can happen if headers are read only (when record is sent second time)
        logger.log(WARNING, "failed to inject span context. sending record second time?", t);
      }
    }
    producerInstrumenter.end(context, request, null, null);
  }
}
