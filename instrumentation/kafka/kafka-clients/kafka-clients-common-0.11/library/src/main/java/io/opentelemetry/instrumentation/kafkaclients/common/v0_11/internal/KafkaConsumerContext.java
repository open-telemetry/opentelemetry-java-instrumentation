/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal;

import com.google.auto.value.AutoValue;
import io.opentelemetry.context.Context;
import java.util.List;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
@AutoValue
public abstract class KafkaConsumerContext {

  static KafkaConsumerContext create(
      @Nullable Context context,
      @Nullable String consumerGroup,
      @Nullable String clientId,
      @Nullable List<String> bootstrapServers) {
    return new AutoValue_KafkaConsumerContext(context, consumerGroup, clientId, bootstrapServers);
  }

  @Nullable
  public abstract Context getContext();

  @Nullable
  abstract String getConsumerGroup();

  @Nullable
  abstract String getClientId();

  @Nullable
  abstract List<String> getBootstrapServers();
}
