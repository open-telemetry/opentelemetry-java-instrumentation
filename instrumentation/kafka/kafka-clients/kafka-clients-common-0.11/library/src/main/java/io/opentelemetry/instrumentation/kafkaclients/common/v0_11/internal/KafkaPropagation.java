/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.javaagent.tooling.muzzle.NoMuzzle;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import org.apache.kafka.clients.ApiVersions;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.record.RecordBatch;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class KafkaPropagation {

  private static final KafkaHeadersSetter setter = KafkaHeadersSetter.INSTANCE;
  private static final boolean HAS_MAX_USABLE_PRODUCE_MAGIC = hasMaxUsableProduceMagic();

  // Do not inject headers for batch versions below 2
  // This is how similar check is being done in Kafka client itself:
  // https://github.com/apache/kafka/blob/05fcfde8f69b0349216553f711fdfc3f0259c601/clients/src/main/java/org/apache/kafka/common/record/MemoryRecordsBuilder.java#L411-L412
  // Also, do not inject headers if specified by JVM option or environment variable
  // This can help in mixed client environments where clients < 0.11 that do not support
  // headers attempt to read messages that were produced by clients > 0.11 and the magic
  // value of the broker(s) is >= 2
  public static boolean shouldPropagate(ApiVersions apiVersions) {
    return !HAS_MAX_USABLE_PRODUCE_MAGIC
        || maxUsableProduceMagic(apiVersions) >= RecordBatch.MAGIC_VALUE_V2;
  }

  public static boolean propagatesSpanContext(TextMapPropagator propagator) {
    SpanContext expected =
        SpanContext.create(
            "00000000000000000000000000000001",
            "0000000000000001",
            TraceFlags.getSampled(),
            TraceState.getDefault());
    Map<String, String> carrier = new HashMap<>();
    propagator.inject(Context.root().with(Span.wrap(expected)), carrier, Map::put);
    Context extracted =
        propagator.extract(
            Context.root(),
            carrier,
            new TextMapGetter<Map<String, String>>() {
              @Override
              public Iterable<String> keys(Map<String, String> carrier) {
                return carrier.keySet();
              }

              @Nullable
              @Override
              public String get(@Nullable Map<String, String> carrier, String key) {
                return carrier == null ? null : carrier.get(key);
              }
            });
    SpanContext actual = Span.fromContext(extracted).getSpanContext();
    return expected.getTraceId().equals(actual.getTraceId())
        && expected.getSpanId().equals(actual.getSpanId());
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
    } catch (NoSuchMethodException ignored) {
      return false;
    }
  }

  public static <K, V> ProducerRecord<K, V> propagateContext(
      Context context, ProducerRecord<K, V> record) {
    return propagateContext(
        GlobalOpenTelemetry.getPropagators().getTextMapPropagator(), context, record);
  }

  public static <K, V> ProducerRecord<K, V> propagateContext(
      TextMapPropagator propagator, Context context, ProducerRecord<K, V> record) {
    try {
      inject(propagator, context, record);
    } catch (IllegalStateException ignored) {
      // headers must be read-only from reused record. try again with new one.
      record =
          new ProducerRecord<>(
              record.topic(),
              record.partition(),
              record.timestamp(),
              record.key(),
              record.value(),
              record.headers());

      inject(propagator, context, record);
    }
    return record;
  }

  private static <K, V> void inject(
      TextMapPropagator propagator, Context context, ProducerRecord<K, V> record) {
    propagator.inject(context, record.headers(), setter);
  }

  private KafkaPropagation() {}
}
