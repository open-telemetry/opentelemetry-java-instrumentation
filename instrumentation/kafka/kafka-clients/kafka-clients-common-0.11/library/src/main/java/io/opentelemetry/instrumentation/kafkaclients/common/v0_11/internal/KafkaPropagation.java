/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.tooling.muzzle.NoMuzzle;
import org.apache.kafka.clients.ApiVersions;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.record.RecordBatch;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class KafkaPropagation {

  private static final KafkaHeadersSetter SETTER = KafkaHeadersSetter.INSTANCE;
  private static final boolean hasMaxUsableProduceMagic = hasMaxUsableProduceMagic();

  // Do not inject headers for batch versions below 2
  // This is how similar check is being done in Kafka client itself:
  // https://github.com/apache/kafka/blob/05fcfde8f69b0349216553f711fdfc3f0259c601/clients/src/main/java/org/apache/kafka/common/record/MemoryRecordsBuilder.java#L411-L412
  // Also, do not inject headers if specified by JVM option or environment variable
  // This can help in mixed client environments where clients < 0.11 that do not support
  // headers attempt to read messages that were produced by clients > 0.11 and the magic
  // value of the broker(s) is >= 2
  public static boolean shouldPropagate(ApiVersions apiVersions) {
    return !hasMaxUsableProduceMagic
        || maxUsableProduceMagic(apiVersions) >= RecordBatch.MAGIC_VALUE_V2;
  }

  @NoMuzzle
  private static byte maxUsableProduceMagic(ApiVersions apiVersions) {
    return apiVersions.maxUsableProduceMagic();
  }

  private static boolean hasMaxUsableProduceMagic() {
    try {
      // missing in kafka 4.x
      ApiVersions.class.getMethod("maxUsableProduceMagic");
      return true;
    } catch (NoSuchMethodException e) {
      return false;
    }
  }

  public static <K, V> ProducerRecord<K, V> propagateContext(
      Context context, ProducerRecord<K, V> record) {
    try {
      inject(context, record);
    } catch (IllegalStateException e) {
      // headers must be read-only from reused record. try again with new one.
      record =
          new ProducerRecord<>(
              record.topic(),
              record.partition(),
              record.timestamp(),
              record.key(),
              record.value(),
              record.headers());

      inject(context, record);
    }
    return record;
  }

  private static <K, V> void inject(Context context, ProducerRecord<K, V> record) {
    GlobalOpenTelemetry.getPropagators()
        .getTextMapPropagator()
        .inject(context, record.headers(), SETTER);
  }

  private KafkaPropagation() {}
}
