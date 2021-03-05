/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkaclients;

import io.opentelemetry.instrumentation.api.config.Config;

public final class KafkaClientsConfig {

  public static boolean isPropagationEnabled() {
    return Config.get()
        .getBooleanProperty("otel.instrumentation.kafka.client-propagation.enabled", true);
  }

  public static boolean captureExperimentalSpanAttributes() {
    return Config.get()
        .getBooleanProperty("otel.instrumentation.kafka.experimental-span-attributes", false);
  }

  private KafkaClientsConfig() {}
}
