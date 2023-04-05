/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafka.internal;

import com.google.auto.value.AutoValue;
import io.opentelemetry.context.Context;
import javax.annotation.Nullable;
import org.apache.kafka.clients.consumer.Consumer;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
@AutoValue
public abstract class KafkaConsumerContext {

  static KafkaConsumerContext create(@Nullable Context context, @Nullable Consumer<?, ?> consumer) {
    return new AutoValue_KafkaConsumerContext(context, consumer);
  }

  @Nullable
  public abstract Context getContext();

  @Nullable
  abstract Consumer<?, ?> getConsumer();
}
