/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal;

import java.lang.reflect.Method;
import java.time.Duration;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;

public class KafkaTestUtil {
  private static final Method consumerPollDurationMethod = getConsumerPollDurationMethod();

  private static Method getConsumerPollDurationMethod() {
    try {
      return Consumer.class.getMethod("poll", Duration.class);
    } catch (NoSuchMethodException e) {
      return null;
    }
  }

  @SuppressWarnings("unchecked")
  public static <K, V> ConsumerRecords<K, V> poll(Consumer<K, V> consumer, Duration duration) {
    // not present in early versions
    if (consumerPollDurationMethod != null) {
      try {
        return (ConsumerRecords<K, V>) consumerPollDurationMethod.invoke(consumer, duration);
      } catch (Exception exception) {
        throw new IllegalStateException(exception);
      }
    }
    // not present in 4.x
    return consumer.poll(duration.toMillis());
  }

  private KafkaTestUtil() {}
}
