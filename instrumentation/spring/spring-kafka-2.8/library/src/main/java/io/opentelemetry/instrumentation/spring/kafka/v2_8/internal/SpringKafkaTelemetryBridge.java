/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.kafka.v2_8.internal;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaProcessRequest;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaReceiveRequest;
import javax.annotation.Nullable;
import org.springframework.kafka.listener.BatchInterceptor;
import org.springframework.kafka.listener.RecordInterceptor;

/**
 * Shares interceptor implementations between library and javaagent instrumentation.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 *
 * <p>This bridge is unsupported and is not part of the public instrumentation API.
 */
public final class SpringKafkaTelemetryBridge {

  public static <K, V> RecordInterceptor<K, V> createRecordInterceptor(
      Instrumenter<KafkaProcessRequest, Void> instrumenter,
      @Nullable RecordInterceptor<K, V> decorated) {
    return new InstrumentedRecordInterceptor<>(instrumenter, decorated);
  }

  public static <K, V> BatchInterceptor<K, V> createBatchInterceptor(
      Instrumenter<KafkaReceiveRequest, Void> instrumenter,
      @Nullable BatchInterceptor<K, V> decorated) {
    return new InstrumentedBatchInterceptor<>(instrumenter, decorated);
  }

  private SpringKafkaTelemetryBridge() {}
}
