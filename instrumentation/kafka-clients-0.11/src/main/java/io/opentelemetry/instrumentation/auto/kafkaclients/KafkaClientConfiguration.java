/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.kafkaclients;

import io.opentelemetry.instrumentation.api.config.Config;

public final class KafkaClientConfiguration {

  public static boolean isPropagationEnabled() {
    return Config.get().getBooleanProperty("otel.kafka.client.propagation.enabled", true);
  }

  private KafkaClientConfiguration() {}
}
