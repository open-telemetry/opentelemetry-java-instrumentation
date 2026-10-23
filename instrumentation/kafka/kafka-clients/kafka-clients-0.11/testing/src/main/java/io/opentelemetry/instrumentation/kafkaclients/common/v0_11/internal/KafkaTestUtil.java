/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Duration;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;

class KafkaTestUtil {
  private static final Method consumerPollDurationMethod = getConsumerPollDurationMethod();

  private static Method getConsumerPollDurationMethod() {
    try {
      return Consumer.class.getMethod("poll", Duration.class);
    } catch (NoSuchMethodException ignored) {
      return null;
    }
  }

  @SuppressWarnings("unchecked")
  static <K, V> ConsumerRecords<K, V> poll(Consumer<K, V> consumer, Duration duration) {
    // not present in early versions
    if (consumerPollDurationMethod != null) {
      try {
        return (ConsumerRecords<K, V>) consumerPollDurationMethod.invoke(consumer, duration);
      } catch (InvocationTargetException e) {
        Throwable cause = e.getCause();
        if (cause instanceof RuntimeException) {
          throw (RuntimeException) cause;
        }
        if (cause instanceof Error) {
          throw (Error) cause;
        }
        throw new IllegalStateException(cause);
      } catch (ReflectiveOperationException e) {
        throw new IllegalStateException(e);
      }
    }
    // not present in 4.x
    return consumer.poll(duration.toMillis());
  }

  private KafkaTestUtil() {}
}
