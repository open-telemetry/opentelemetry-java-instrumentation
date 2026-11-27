/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.kafka.v2_8;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaProcessRequest;
import javax.annotation.Nullable;
import org.springframework.kafka.listener.RecordInterceptor;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class SpringKafkaTelemetryFactory {

  public static <K, V> RecordInterceptor<K, V> createRecordInterceptor(
      Instrumenter<KafkaProcessRequest, Void> processInstrumenter,
      @Nullable RecordInterceptor<K, V> decoratedInterceptor) {
    return new InstrumentedRecordInterceptor<>(processInstrumenter, decoratedInterceptor);
  }

  private SpringKafkaTelemetryFactory() {}
}
